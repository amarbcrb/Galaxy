package galaxy.app

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import galaxy.core.brain.Action
import galaxy.core.brain.Brain
import galaxy.core.brain.BrainState
import galaxy.core.brain.Event
import galaxy.core.brain.Moment
import galaxy.core.face.Gaze
import galaxy.core.face.Look
import galaxy.core.input.Gesture
import galaxy.core.input.GestureClassifier
import galaxy.core.input.Motion
import galaxy.core.input.MotionDetector
import java.time.LocalDateTime

/**
 * Connects the Android world (touch, sensors, battery, speakers) to Galaxy's [Brain],
 * and exposes what the screen needs as Compose state. Used from the main thread only.
 */
class GalaxyController(context: Context) {
    private val store = SettingsStore(context)
    private val brain = Brain()
    private val gestures = GestureClassifier()
    private val motion = MotionDetector()
    private val gaze = Gaze()
    private val speaker = Speaker(context)
    private val sounds = SoundPlayer()
    private val haptics = Haptics(context)

    var state by mutableStateOf(BrainState())
        private set
    var look by mutableStateOf(Look.CENTER)
        private set
    var settings by mutableStateOf(store.load())
        private set
    var menuOpen by mutableStateOf(false)

    private var finger: Look? = null
    private var touchArea = 1f to 1f
    private var tiltX = 0f
    private var resumed = false

    init {
        brain.settings = settings.toBrain()
    }

    private fun now() = Moment(SystemClock.elapsedRealtime(), LocalDateTime.now())

    fun tick() {
        if (!resumed) return
        gestures.onHold(SystemClock.uptimeMillis()).forEach(::onGesture)
        perform(brain.tick(now()))
        look = gaze.target(SystemClock.elapsedRealtime(), finger, tiltX, awake = !state.asleep)
    }

    fun onDown(x: Float, y: Float, uptimeMillis: Long, width: Float, height: Float) {
        finger = Look(x / width * 2 - 1, y / height * 2 - 1)
        touchArea = width to height
        gestures.onDown(x, y, uptimeMillis, width, height).forEach(::onGesture)
    }

    fun onMove(x: Float, y: Float, uptimeMillis: Long) {
        val (w, h) = touchArea
        finger = Look(x / w * 2 - 1, y / h * 2 - 1)
        gestures.onMove(x, y, uptimeMillis).forEach(::onGesture)
    }

    fun onUp(x: Float, y: Float, uptimeMillis: Long) {
        finger = null
        gestures.onUp(x, y, uptimeMillis).forEach(::onGesture)
    }

    private fun onGesture(gesture: Gesture) {
        val event = when (gesture) {
            Gesture.TAP -> Event.Tap
            Gesture.DOUBLE_TAP -> Event.DoubleTap
            Gesture.LONG_PRESS -> Event.LongPress
            Gesture.PET -> Event.Pet
            Gesture.SWIPE_UP -> {
                menuOpen = true
                Event.MenuOpened
            }
        }
        send(event)
    }

    /** Accelerometer sample in device axes; [rotation] is the display rotation (Surface.ROTATION_*). */
    fun onAccelerometer(x: Float, y: Float, z: Float, timeMillis: Long, rotation: Int) {
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
        // Horizontal gravity in screen coordinates; the eyes slide towards the lower side.
        val screenX = when (rotation) {
            Surface.ROTATION_90 -> -motion.gravityY
            Surface.ROTATION_180 -> -motion.gravityX
            Surface.ROTATION_270 -> motion.gravityY
            else -> motion.gravityX
        }
        tiltX = (-screenX / MotionDetector.GRAVITY * 1.5f).coerceIn(-1f, 1f)
    }

    fun onBattery(level: Int, charging: Boolean) = send(Event.Battery(level, charging))

    fun startTimer(minutes: Int) {
        menuOpen = false
        send(Event.StartTimer(minutes * 60_000L))
    }

    fun cancelTimer() = send(Event.CancelTimer)

    fun updateSettings(newSettings: UserSettings) {
        settings = newSettings
        store.save(newSettings)
        brain.settings = newSettings.toBrain()
    }

    fun onResume() {
        resumed = true
    }

    fun onPause() {
        resumed = false
        finger = null
        speaker.stop()
        sounds.pause()
    }

    fun onDestroy() {
        speaker.shutdown()
        sounds.release()
    }

    private fun send(event: Event) {
        perform(brain.onEvent(event, now()))
    }

    private fun perform(actions: List<Action>) {
        state = brain.state
        for (action in actions) {
            when (action) {
                is Action.Speak -> if (settings.soundOn) speaker.speak(action.text)
                is Action.PlaySound -> if (settings.soundOn) sounds.play(action.cue)
                is Action.Vibrate -> haptics.vibrate(action.pattern)
            }
        }
    }
}
