package uk.co.home.chromecastremote

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager

/** Explicit touch feedback, controlled by the app preference rather than keyboard feedback. */
internal class ButtonVibration(context: Context, private val enabled: () -> Boolean) {
    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
    fun tap() {
        if (!enabled() || !vibrator.hasVibrator()) return
        val pulse = VibrationEffect.createOneShot(18, 90)
        if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(pulse, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
        else legacyPulse(pulse)
    }
    @Suppress("DEPRECATION") // AudioAttributes is the supported classification on API 26–32.
    private fun legacyPulse(pulse: VibrationEffect) = vibrator.vibrate(pulse, attributes)
}
