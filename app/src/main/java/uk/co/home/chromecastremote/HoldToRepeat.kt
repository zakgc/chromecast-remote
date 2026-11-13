package uk.co.home.chromecastremote

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button

/** Repeated short taps require no held key on the TV, so cancellation cannot leave it scrolling. */
class HoldToRepeat(private val session: () -> Long?) {
    private val handler = Handler(Looper.getMainLooper())
    private var active: Button? = null
    private var token: Long? = null
    private val repeat = object : Runnable {
        override fun run() {
            val button = active ?: return
            if (!button.isEnabled || session() != token) { cancel(); return }
            button.performClick()
            handler.postDelayed(this, REPEAT_MS)
        }
    }
    @SuppressLint("ClickableViewAccessibility") // Native Button.performClick owns every action; accessibility remains a single click (covered by tests).
    fun attach(button: Button) {
        button.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancel()
                    val current = session()
                    if (!button.isEnabled || current == null) return@setOnTouchListener false
                    active = button; token = current
                    button.isPressed = true
                    button.parent?.requestDisallowInterceptTouchEvent(true)
                    touched.performClick()
                    handler.postDelayed(repeat, INITIAL_DELAY_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active === button && (event.x < 0 || event.y < 0 || event.x >= button.width || event.y >= button.height)) cancel()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { cancel(); true }
                else -> active === button
            }
        }
    }
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        active?.isPressed = false
        active?.parent?.requestDisallowInterceptTouchEvent(false)
        active = null; token = null
    }
    companion object {
        const val INITIAL_DELAY_MS = 400L
        const val REPEAT_MS = 150L
    }
}
