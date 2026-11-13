package uk.co.home.chromecastremote

import android.os.Build
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class ButtonVibrationTest {
    @Suppress("DEPRECATION")
    @Test fun appPreferenceControlsPulsesAndSkipsMissingHardware() {
        val context = RuntimeEnvironment.getApplication()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
            else context.getSystemService(Vibrator::class.java)
        val shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
        var enabled = false
        val feedback = ButtonVibration(context) { enabled }
        feedback.tap(); assertFalse(shadow.isVibrating)
        enabled = true
        feedback.tap(); assertTrue(shadow.isVibrating)
        assertEquals(18L, shadow.milliseconds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))
        enabled = false
        feedback.tap(); assertFalse(shadow.isVibrating)
        enabled = true; shadow.setHasVibrator(false)
        feedback.tap(); assertFalse(shadow.isVibrating)
    }
}
