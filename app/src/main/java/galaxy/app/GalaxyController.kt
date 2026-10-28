package galaxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import galaxy.core.Emotion
import galaxy.core.brain.Action
import galaxy.core.brain.Brain
import galaxy.core.brain.BrainState
import galaxy.core.brain.Command
import galaxy.core.brain.Event
import galaxy.core.brain.Moment
import galaxy.core.brain.VoiceCommands
import galaxy.core.face.EyeColors
import galaxy.core.face.FacePresence
import galaxy.core.face.Gaze
import galaxy.core.face.Look
import galaxy.core.input.Gesture
import galaxy.core.input.GestureClassifier
import galaxy.core.input.HeadMotion
import galaxy.core.input.Motion
import galaxy.core.input.MotionDetector
import galaxy.core.input.ScreenAxes
import java.time.LocalDateTime

/**
 * Connects the Android world (touch, sensors, camera, microphone, battery, speakers) to
 * Galaxy's [Brain], and exposes what the screen needs as Compose state. Main thread only.
 */
class GalaxyController(
    private val activity: ComponentActivity,
    /** Current display rotation as Surface.ROTATION_* (0..3). */
    private val displayRotation: () -> Int,
) {
    private val store = SettingsStore(activity)
    private val brain = Brain()
    private val gestures = GestureClassifier()
    private val motion = MotionDetector()
    private val headMotion = HeadMotion()
    private val gaze = Gaze()
    private val presence = FacePresence()
    private val speaker = Speaker(activity)
    private val sounds = SoundPlayer()
    private val haptics = Haptics(activity)
    private val voice = VoiceListener(
        activity,
        continuous = true,
        onPhrases = ::onPhrases,
        onStatus = { voiceStatus = it },
        onErrorCode = { voiceError = it },
    )

    /** Push-to-talk: listens once, without Android's dialog. */
    private val ear = VoiceListener(
        activity,
        continuous = false,
        onPhrases = ::onPhrases,
        onStatus = ::onEarStatus,
        onErrorCode = { voiceError = it },
    )
    private val camera = FaceCamera(activity, ::onFace)

    var state by mutableStateOf(BrainState())
        private set
    var look by mutableStateOf(Look.CENTER)
        private set
    var head by mutableStateOf(Look.CENTER)
        private set
    var settings by mutableStateOf(store.load())
        private set
    var menuOpen by mutableStateOf(false)
    var secretMenuOpen by mutableStateOf(false)
    var voiceStatus by mutableStateOf(VoiceStatus.OFF)
        private set
    var voiceError by mutableStateOf<Int?>(null)
        private set

    /** Galaxy is listening after a two-finger tap. */
    var listening by mutableStateOf(false)
        private set

    /** Bumped when push-to-talk cannot work here, so the screen opens Android's speech dialog instead. */
    var speechDialogRequests by mutableStateOf(0)
        private set

    /** The last thing Galaxy heard, shown briefly as a subtitle. */
    var heard by mutableStateOf<String?>(null)
        private set
    private var heardAt = 0L
    private var lastCommand: Command? = null
    private var lastCommandAt = 0L

    private var finger: Look? = null
    private var lastTouch = Look.CENTER
    private var touchArea = 1f to 1f
    private var tiltX = 0f
    private var rotation = 0
    private var resumed = false
    private var cameraRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var wasNight = false

    init {
        brain.settings = settings.toBrain()
    }

    private fun now() = Moment(SystemClock.elapsedRealtime(), LocalDateTime.now())

    fun tick() {
        if (!resumed) return
        // A half turn (landscape to reverse landscape) changes no configuration, so check here.
        val currentRotation = displayRotation()
        if (currentRotation != rotation) {
            rotation = currentRotation
            camera.setRotation(currentRotation)
        }
        gestures.onHold(SystemClock.uptimeMillis()).forEach(::onGesture)
        perform(brain.tick(now()))
        if (heard != null && SystemClock.elapsedRealtime() - heardAt > SUBTITLE_MILLIS) heard = null
        look = gaze.target(SystemClock.elapsedRealtime(), finger, presence.look, tiltX, awake = !state.asleep)
        if (state.night != wasNight) {
            wasNight = state.night
            updateFeatures()
        }
    }

    // Touch

    fun onDown(x: Float, y: Float, uptimeMillis: Long, width: Float, height: Float) {
        finger = Look(x / width * 2 - 1, y / height * 2 - 1)
        touchArea = width to height
        gestures.onDown(x, y, uptimeMillis, width, height).forEach(::onGesture)
    }

    fun onPointers(count: Int, uptimeMillis: Long) = gestures.onPointerCount(count, uptimeMillis)

    fun onMove(x: Float, y: Float, uptimeMillis: Long) {
        val (w, h) = touchArea
        finger = Look(x / w * 2 - 1, y / h * 2 - 1)
        gestures.onMove(x, y, uptimeMillis).forEach(::onGesture)
    }

    fun onUp(x: Float, y: Float, uptimeMillis: Long) {
        val (w, h) = touchArea
        lastTouch = Look(x / w * 2 - 1, y / h * 2 - 1)
        finger = null
        gestures.onUp(x, y, uptimeMillis).forEach(::onGesture)
    }

    private fun onGesture(gesture: Gesture) {
        val event = when (gesture) {
            // Hands up and scared: a tap "shoots" right where the finger is.
            Gesture.TAP -> if (state.emotion == Emotion.SCARED) Event.Shoot(lastTouch.x, lastTouch.y) else Event.Tap
            Gesture.DOUBLE_TAP -> Event.DoubleTap
            Gesture.LONG_PRESS -> Event.LongPress
            Gesture.PET -> Event.Pet
            Gesture.SWIPE_UP -> {
                menuOpen = true
                Event.MenuOpened
            }
            Gesture.TWO_FINGER_TAP -> {
                listen()
                return
            }
            Gesture.TWO_FINGER_HOLD -> {
                secretMenuOpen = true
                Event.MenuOpened
            }
        }
        send(event)
    }

    // Sensors

    /** Accelerometer sample in device axes. */
    fun onAccelerometer(x: Float, y: Float, z: Float, timeMillis: Long) {
        motion.onSample(x, y, z, timeMillis).forEach { m ->
            send(
                when (m) {
                    Motion.SHAKE -> Event.Shake
                    Motion.FACE_DOWN -> Event.FaceDown
                    Motion.FLAT_STILL -> Event.FlatStill
                    Motion.PICKED_UP -> Event.PickedUp
                },
            )
        }
        // The eyes slide towards the lower side of the screen.
        val (screenX, _) = ScreenAxes.remap(motion.gravityX, motion.gravityY, rotation)
        tiltX = (-screenX / MotionDetector.GRAVITY * 1.5f).coerceIn(-1f, 1f)
    }

    /** Gyroscope sample in device axes (rad/s). */
    fun onGyroscope(x: Float, y: Float, timeMillis: Long) {
        val (aroundScreenX, aroundScreenY) = ScreenAxes.remap(x, y, rotation)
        val spun = headMotion.onGyro(yawRate = aroundScreenY, pitchRate = aroundScreenX, timeMillis = timeMillis)
        head = Look(headMotion.yaw, headMotion.pitch)
        if (spun) send(Event.Shake)
    }

    private fun onFace(face: Look?) {
        if (presence.onFrame(face, SystemClock.elapsedRealtime())) send(Event.FaceSeen)
    }

    /** Push-to-talk: Galaxy perks up, beeps and listens to one phrase. */
    fun listen() {
        if (listening) return
        menuOpen = false
        secretMenuOpen = false
        voice.stop()
        listening = true
        send(Event.Act(Emotion.CURIOUS))
        // Let the continuous listener release the microphone, and the beep finish, first.
        handler.postDelayed({ if (listening) ear.start() }, MIC_RELEASE_MILLIS)
    }

    private fun onEarStatus(status: VoiceStatus) {
        when (status) {
            VoiceStatus.OFF -> {
                listening = false
                updateFeatures()
            }
            VoiceStatus.UNAVAILABLE, VoiceStatus.NO_PERMISSION -> {
                listening = false
                speechDialogRequests++
            }
            VoiceStatus.STARTING, VoiceStatus.LISTENING -> Unit
        }
    }

    /**
     * Frees the microphone for Android's speech dialog: two recognizers at once make the dialog
     * hear nothing. Listening starts again when Galaxy comes back to the front.
     */
    fun stopListening() {
        voice.stop()
    }

    fun showSubtitle(text: String) {
        heard = text
        heardAt = SystemClock.elapsedRealtime()
    }

    /** Speech heard, from continuous listening ([final] false for words still coming) or the speech dialog. */
    fun onPhrases(phrases: List<String>, final: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        heard = phrases.first()
        heardAt = now
        val found = phrases.firstNotNullOfOrNull { VoiceCommands.parse(it) } ?: return
        // Partial and final results repeat the same words: act only once.
        if (found == lastCommand && now - lastCommandAt < REPEAT_MILLIS) return
        lastCommand = found
        lastCommandAt = now
        command(found)
    }

    fun onBattery(level: Int, charging: Boolean) = send(Event.Battery(level, charging))

    // Menus

    fun command(command: Command) {
        secretMenuOpen = false
        menuOpen = false
        when (command) {
            is Command.EyeColor -> {
                updateSettings(settings.copy(eyeColor = command.color ?: EyeColors.next(settings.eyeColor)))
                send(command.toEvent())
            }
            Command.Reveal -> send(Event.Reveal(settings.prediction))
            else -> send(command.toEvent())
        }
    }

    fun startTimer(minutes: Int) = command(Command.Timer(minutes))

    fun cancelTimer() = send(Event.CancelTimer)

    fun updateSettings(newSettings: UserSettings) {
        settings = newSettings
        store.save(newSettings)
        brain.settings = newSettings.toBrain()
        updateFeatures()
    }

    // Permissions and features

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /** Permissions needed by the features that are turned on but not yet allowed. */
    fun missingPermissions(): List<String> = buildList {
        if (settings.voiceOn && !hasPermission(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        if (settings.cameraOn && !hasPermission(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
    }

    /** Turns off the features whose permission was refused, so Galaxy stops asking. */
    fun onPermissionsResult(granted: Map<String, Boolean>) {
        var updated = settings
        if (granted[Manifest.permission.RECORD_AUDIO] == false) updated = updated.copy(voiceOn = false)
        if (granted[Manifest.permission.CAMERA] == false) updated = updated.copy(cameraOn = false)
        updateSettings(updated)
    }

    private fun updateFeatures() {
        // Galaxy stops listening and watching at night, while he sleeps.
        val awakeHours = resumed && !state.night
        if (listening) {
            // Push-to-talk owns the microphone for now.
            voice.stop()
        } else if (awakeHours && settings.voiceOn && hasPermission(Manifest.permission.RECORD_AUDIO)) voice.start() else voice.stop()

        val wantCamera = awakeHours && settings.cameraOn && hasPermission(Manifest.permission.CAMERA)
        if (wantCamera != cameraRunning) {
            cameraRunning = wantCamera
            if (wantCamera) {
                camera.start(activity, rotation)
            } else {
                camera.stop()
                presence.forget()
            }
        }
    }

    // Lifecycle

    fun onResume() {
        resumed = true
        rotation = displayRotation()
        updateFeatures()
    }

    fun onPause() {
        resumed = false
        ear.stop()
        finger = null
        updateFeatures()
        speaker.stop()
        sounds.pause()
    }

    fun onDestroy() {
        voice.stop()
        ear.stop()
        camera.release()
        speaker.shutdown()
        sounds.release()
    }

    private companion object {
        const val SUBTITLE_MILLIS = 3_000L
        const val REPEAT_MILLIS = 2_500L
        const val MIC_RELEASE_MILLIS = 400L
    }

    private fun send(event: Event) {
        perform(brain.onEvent(event, now()))
    }

    private fun perform(actions: List<Action>) {
        state = brain.state
        for (action in actions) {
            when (action) {
                is Action.Speak -> if (settings.soundOn) {
                    if (settings.speakWords) speaker.speak(action.text) else sounds.babble(action.text)
                }
                is Action.PlaySound -> if (settings.soundOn) sounds.play(action.cue)
                is Action.Vibrate -> haptics.vibrate(action.pattern)
            }
        }
    }
}
