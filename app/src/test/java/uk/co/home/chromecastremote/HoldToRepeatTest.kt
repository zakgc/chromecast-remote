package uk.co.home.chromecastremote

import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
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
class HoldToRepeatTest {
    private fun touch(button: Button, action: Int, x: Float = 50f) {
        val event = MotionEvent.obtain(0, 0, action, x, 50f, 0)
        button.dispatchTouchEvent(event); event.recycle()
    }
    @Test fun pressIsImmediateThenRepeatsAndStopsExactlyOnRelease() {
        var presses = 0
        val button = Button(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 100, 100); setOnClickListener { presses++ } }
        val repeat = HoldToRepeat { 1L }.apply { attach(button) }
        touch(button, MotionEvent.ACTION_DOWN)
        assertEquals(1, presses)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        assertTrue("Keeping the finger down must generate more navigation taps", presses >= 2)
        val beforeRelease = presses
        touch(button, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(beforeRelease, presses)
        assertFalse(button.isPressed)
        repeat.cancel()
    }
    @Test fun cancellationLeavingTheButtonAndSessionChangesCannotReplayAHold() {
        var presses = 0
        var session: Long? = 1L
        val button = Button(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 100, 100); setOnClickListener { presses++ } }
        val repeat = HoldToRepeat { session }.apply { attach(button) }
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(1, presses)
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_MOVE, 101f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(2, presses)
        touch(button, MotionEvent.ACTION_DOWN); session = 2L
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(3, presses)
        touch(button, MotionEvent.ACTION_DOWN); session = null
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(4, presses)
        repeat.cancel()
    }
    @Test fun accessibilityClicksStaySingleAndDoNotStartARepeat() {
        var presses = 0
        val button = Button(RuntimeEnvironment.getApplication()).apply { setOnClickListener { presses++ } }
        val repeat = HoldToRepeat { 1L }.apply { attach(button) }
        button.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1, presses)
        repeat.cancel()
    }
}
