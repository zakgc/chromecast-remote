package uk.co.home.chromecastremote

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.Button

/** Keep OK down on the TV until the finger lifts; accessibility clicks remain ordinary taps. */
class HoldToSelect(
    private val session: () -> Long?,
    private val send: (Long, Boolean) -> Boolean,
    private val feedback: () -> Unit
) {
    private var active: Button? = null
    private var token: Long? = null

    @SuppressLint("ClickableViewAccessibility") // The existing click listener remains available to accessibility.
    fun attach(button: Button) {
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancel()
                    val current = session()
                    if (!button.isEnabled || current == null) return@setOnTouchListener false
                    if (!send(current, true)) return@setOnTouchListener true
                    active = button; token = current
                    button.isPressed = true
                    button.parent?.requestDisallowInterceptTouchEvent(true)
                    feedback()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active === button && (session() != token || event.x < 0 || event.y < 0 ||
                            event.x >= button.width || event.y >= button.height)) cancel()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { cancel(); true }
                else -> active === button
            }
        }
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { if (active === button) cancel() }
        })
    }

    fun cancelIfSessionChanged() {
        if (active != null && session() != token) cancel()
    }

    fun cancel() {
        val previous = token
        active?.isPressed = false
        active?.parent?.requestDisallowInterceptTouchEvent(false)
        active = null; token = null
        if (previous != null && session() == previous) send(previous, false)
    }
}
