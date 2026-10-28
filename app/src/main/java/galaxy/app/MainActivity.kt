package galaxy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import galaxy.core.brain.Overlay
import galaxy.core.face.Expressions
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var controller: GalaxyController
    private lateinit var sensorManager: SensorManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBatteryIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = GalaxyController(this, ::displayRotation)
        sensorManager = getSystemService(SensorManager::class.java)
        hideSystemBars()

        setContent {
            MaterialTheme(colorScheme = galaxyColors) {
                GalaxyScreen(controller)
            }
            val state = controller.state
            LaunchedEffect(state.keepScreenOn) {
                if (state.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            LaunchedEffect(state.night) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (state.night) {
                        NIGHT_BRIGHTNESS
                    } else {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
        hideSystemBars()
        for (type in intArrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)) {
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::onBatteryIntent)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        unregisterReceiver(batteryReceiver)
        controller.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val millis = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> controller.onAccelerometer(v[0], v[1], v[2], millis)
            Sensor.TYPE_GYROSCOPE -> controller.onGyroscope(v[0], v[1], millis)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        controller.onBattery(level * 100 / scale, plugged)
    }

    private fun displayRotation(): Int = if (Build.VERSION.SDK_INT >= 30) {
        display?.rotation ?: 0
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val NIGHT_BRIGHTNESS = 0.02f
    }
}

private val galaxyColors = darkColorScheme(
    primary = Color(Expressions.CYAN.toInt()),
    background = Color.Black,
    surface = Color(0xFF111118),
)

@Composable
private fun GalaxyScreen(controller: GalaxyController) {
    LaunchedEffect(Unit) {
        while (true) {
            controller.tick()
            delay(100)
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.onPermissionsResult(it)
    }
    val settings = controller.settings
    LaunchedEffect(settings.nameAsked, settings.voiceOn, settings.cameraOn) {
        val missing = controller.missingPermissions()
        if (settings.nameAsked && missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    val state = controller.state
    val color = state.emotion.color().copy(alpha = if (state.night) 0.35f else 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    controller.onDown(down.position.x, down.position.y, down.uptimeMillis, w, h)
                    var last = down.position
                    var lastTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        controller.onPointers(event.changes.count { it.pressed }, change.uptimeMillis)
                        last = change.position
                        lastTime = change.uptimeMillis
                        if (!change.pressed) break
                        controller.onMove(last.x, last.y, lastTime)
                    }
                    controller.onUp(last.x, last.y, lastTime)
                }
            },
    ) {
        when (state.overlay) {
            Overlay.CLOCK -> ClockDigits(color)
            Overlay.TIMER -> Digits(formatCountdown(state.timerRemainingMillis ?: 0), color)
            Overlay.NONE -> Face(state.emotion, controller.look, head = { controller.head }, dim = state.night)
        }
    }

    if (controller.menuOpen) {
        GalaxyMenu(
            settings = controller.settings,
            timerRemainingMillis = state.timerRemainingMillis,
            onStartTimer = controller::startTimer,
            onCancelTimer = controller::cancelTimer,
            onSettingsChange = controller::updateSettings,
            onDismiss = { controller.menuOpen = false },
        )
    }

    if (controller.secretMenuOpen) {
        SecretMenu(onCommand = controller::command, onDismiss = { controller.secretMenuOpen = false })
    }

    if (!controller.settings.nameAsked) {
        NameDialog { name ->
            controller.updateSettings(controller.settings.copy(name = name, nameAsked = true))
        }
    }
}
