package galaxy.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import galaxy.core.brain.VibrationPattern

class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun vibrate(pattern: VibrationPattern) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (pattern) {
            VibrationPattern.SHORT -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            VibrationPattern.ALARM -> VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
            VibrationPattern.SHOT -> VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 300), -1)
        }
        v.vibrate(effect)
    }
}
