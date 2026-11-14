package uk.co.home.chromecastremote

import android.os.Looper
import android.view.MotionEvent
import android.view.View
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
class HoldToSelectTest {
    private fun touch(button: Button, action: Int, x: Float = 50f) {
        val event = MotionEvent.obtain(0, 0, action, x, 50f, 0)
        button.dispatchTouchEvent(event); event.recycle()
    }

    @Test fun holdsUntilReleaseWithoutRepeatingOrAddingAClick() {
        val events = mutableListOf<Boolean>()
        var clicks = 0
        var feedback = 0
        val button = Button(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 100, 100); setOnClickListener { clicks++ }
        }
        val hold = HoldToSelect({ 1L }, { _, pressed -> events.add(pressed); true }, { feedback++ })
        hold.attach(button)
        touch(button, MotionEvent.ACTION_DOWN)
        assertTrue(button.isPressed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(true), events)
        touch(button, MotionEvent.ACTION_UP)
        assertEquals(listOf(true, false), events)
        assertFalse(button.isPressed)
        assertEquals(0, clicks)
        assertEquals(1, feedback)
        button.performClick()
        assertEquals(1, clicks)
        assertEquals(listOf(true, false), events)
    }

    @Test fun quickTapsAndCancelledGesturesReleaseExactlyOnce() {
        val events = mutableListOf<Boolean>()
        val button = Button(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 100, 100) }
        val hold = HoldToSelect({ 1L }, { _, pressed -> events.add(pressed); true }, {})
        hold.attach(button)
        for (action in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
            events.clear()
            touch(button, MotionEvent.ACTION_DOWN); touch(button, action); hold.cancel()
            assertEquals(listOf(true, false), events)
            assertFalse(button.isPressed)
        }
        events.clear()
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_MOVE, 101f)
        touch(button, MotionEvent.ACTION_UP)
        assertEquals(listOf(true, false), events)
    }

    @Test fun sessionChangesNeverSendAReleaseToAnotherTv() {
        var session: Long? = 1L
        val events = mutableListOf<Pair<Long, Boolean>>()
        val button = Button(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 100, 100) }
        val hold = HoldToSelect({ session }, { token, pressed -> events.add(token to pressed); true }, {})
        hold.attach(button)
        touch(button, MotionEvent.ACTION_DOWN)
        session = 2L; hold.cancelIfSessionChanged()
        assertFalse(button.isPressed)
        touch(button, MotionEvent.ACTION_UP)
        assertEquals(listOf(1L to true), events)
        touch(button, MotionEvent.ACTION_DOWN)
        hold.cancel()
        assertEquals(listOf(1L to true, 2L to true, 2L to false), events)
        session = null
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP)
        assertEquals(3, events.size)
    }

    @Test fun failedPressCannotFallThroughToAShortClick() {
        var clicks = 0
        val events = mutableListOf<Boolean>()
        val button = Button(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 100, 100); setOnClickListener { clicks++ }
        }
        HoldToSelect({ 1L }, { _, pressed -> events.add(pressed); false }, {}).attach(button)
        touch(button, MotionEvent.ACTION_DOWN); touch(button, MotionEvent.ACTION_UP)
        assertEquals(listOf(true), events)
        assertEquals(0, clicks)
    }

    @Test fun circularPadReleasesOkWhenTheFingerCrossesIntoAnArrow() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<Boolean>()
        val hold = HoldToSelect({ 1L }, { _, pressed -> events.add(pressed); true }, {})
        val keys = (19..23).associateWith { Button(context) }
        hold.attach(keys.getValue(23))
        val pad = CircularRemotePad(context, keys)
        pad.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY))
        pad.layout(0, 0, 280, 280)
        fun touchPad(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            pad.dispatchTouchEvent(event); event.recycle()
        }
        touchPad(MotionEvent.ACTION_DOWN, 140f, 140f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(true), events)
        touchPad(MotionEvent.ACTION_MOVE, 250f, 140f)
        touchPad(MotionEvent.ACTION_UP, 250f, 140f)
        assertEquals(listOf(true, false), events)
        assertFalse(keys.getValue(23).isPressed)
    }
}
