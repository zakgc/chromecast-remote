package uk.co.home.chromecastremote

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

internal object RemoteColors {
    val background = Color.rgb(25, 28, 34)
    val surface = Color.rgb(37, 42, 51)
    val navy = Color.rgb(23, 43, 73)
    val edge = Color.rgb(57, 68, 81)
    val ink = Color.rgb(241, 244, 250)
    val muted = Color.rgb(164, 175, 191)
    val blue = Color.rgb(121, 169, 245)
}

/** Small vector controls stay crisp at every density, without font-dependent symbols. */
internal class RemoteIcon(private val kind: String, private val color: Int = RemoteColors.ink) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        this.color = this@RemoteIcon.color
    }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg points: Float) {
            val path = Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1]) }
            canvas.drawPath(path, paint)
        }
        when (kind) {
            "up" -> line(6f, 15f, 12f, 9f, 18f, 15f)
            "down" -> line(6f, 9f, 12f, 15f, 18f, 9f)
            "left" -> line(15f, 6f, 9f, 12f, 15f, 18f)
            "right" -> line(9f, 6f, 15f, 12f, 9f, 18f)
            "back" -> { line(11f, 5f, 4f, 12f, 11f, 19f); line(4f, 12f, 21f, 12f) }
            "home" -> line(3f, 10f, 12f, 3f, 21f, 10f, 21f, 21f, 3f, 21f, 3f, 10f)
            "play" -> { line(3f, 5f, 11f, 12f, 3f, 19f, 3f, 5f); line(16f, 5f, 16f, 19f); line(21f, 5f, 21f, 19f) }
            "keyboard" -> {
                canvas.drawRoundRect(2f, 5f, 22f, 19f, 2f, 2f, paint)
                for (y in listOf(9f, 12f)) for (x in listOf(6f, 10f, 14f, 18f)) canvas.drawPoint(x, y, paint)
                line(7f, 16f, 17f, 16f)
            }
            "reconnect" -> {
                val ring = RectF(3f, 3f, 21f, 21f)
                val upper = Path().apply {
                    arcTo(ring, 180f, 135f); lineTo(21f, 8f)
                }
                val lower = Path().apply {
                    arcTo(ring, 0f, 135f); lineTo(3f, 16f)
                }
                canvas.drawPath(upper, paint); canvas.drawPath(lower, paint)
                line(21f, 3f, 21f, 8f, 16f, 8f)
                line(3f, 21f, 3f, 16f, 8f, 16f)
            }
            "settings" -> {
                val path = Path()
                for (i in 0 until 32) {
                    val a = i * Math.PI / 16
                    val r = if (i % 4 == 0 || i % 4 == 3) 10f else 8f
                    val x = 12f + kotlin.math.cos(a).toFloat() * r
                    val y = 12f + kotlin.math.sin(a).toFloat() * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); canvas.drawPath(path, paint); canvas.drawCircle(12f, 12f, 3.5f, paint)
            }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is unused")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Match the square settings button to the selector's measured height, including larger text. */
internal class DeviceTopBar(context: Context) : LinearLayout(context) {
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount != 2) return
        val side = getChildAt(0).measuredHeight
        val settings = getChildAt(1).layoutParams
        if (settings.width != side || settings.height != side) {
            settings.width = side; settings.height = side
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}

/** Supply the available height during the first measure, before ScrollView has a measured height. */
internal class RemoteViewport(context: Context) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        (getChildAt(0) as? RemoteBody)?.viewportHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) 0
            else (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Icon-only buttons do not inherit empty text/compound-drawable offsets. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed with an icon by the remote screen.
internal class RemoteIconButton(context: Context, direction: String, private val sizeDp: Int = 30) : Button(context) {
    private val icon = RemoteIcon(direction)
    override fun onDraw(canvas: Canvas) {
        val side = (sizeDp * resources.displayMetrics.density).toInt()
        val left = (width - side) / 2; val top = (height - side) / 2
        icon.setBounds(left, top, left + side, top + side)
        icon.draw(canvas)
    }
}

/** A clipped, one-line loop keeps the full accessible name and leaves status/controls stationary. */
internal class LoopingDeviceName(context: Context) : TextView(context) {
    private var animation: ValueAnimator? = null
    internal var scrollOffset = 0f; private set
    internal val isLooping get() = animation?.isRunning == true
    init { setSingleLine() }
    private fun stopLoop() { animation?.cancel(); animation = null; scrollOffset = 0f; invalidate() }
    private fun updateLoop() {
        stopLoop()
        val available = width - paddingLeft - paddingRight
        val textWidth = paint.measureText(text.toString())
        if (available <= 0 || textWidth <= available || !isAttachedToWindow || windowVisibility != VISIBLE || !isShown || !ValueAnimator.areAnimatorsEnabled()) return
        val distance = textWidth + 32 * resources.displayMetrics.density
        val cycleMs = (distance / resources.displayMetrics.density / 25 * 1000 + 1200).toLong()
        val pause = 1200f / cycleMs
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleMs; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener {
                scrollOffset = ((it.animatedValue as Float) - pause).coerceAtLeast(0f) / (1 - pause) * distance
                invalidate()
            }
            start()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateLoop() }
    override fun onDetachedFromWindow() { stopLoop(); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); updateLoop() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (isVisible) updateLoop() else stopLoop() }
    override fun onDraw(canvas: Canvas) {
        val name = text.toString()
        paint.color = currentTextColor
        val metrics = paint.fontMetrics
        val baseline = paddingTop + (height - paddingTop - paddingBottom - metrics.top - metrics.bottom) / 2f
        val saved = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        canvas.drawText(name, paddingLeft - scrollOffset, baseline, paint)
        if (isLooping) canvas.drawText(name, paddingLeft + paint.measureText(name) + 32 * resources.displayMetrics.density - scrollOffset, baseline, paint)
        canvas.restoreToCount(saved)
    }
}

/** Real focusable Buttons expose each direction to TalkBack, keyboard and switch access. */
@android.annotation.SuppressLint("ViewConstructor") // Built only in code with the existing command buttons; never inflated from XML.
internal class CircularRemotePad(context: Context, private val keys: Map<Int, Button>) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var touched: Button? = null
    init { setWillNotDraw(false); keys.values.forEach { addView(it) } }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
        keys.forEach { (key, button) ->
            val side = (size * if (key == 23) .38f else .30f).toInt()
            button.measure(MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY))
        }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        keys.forEach { (key, button) ->
            val cx = width * when (key) { 21 -> .15f; 22 -> .85f; else -> .5f }
            val cy = height * when (key) { 19 -> .15f; 20 -> .85f; else -> .5f }
            val x = (cx - button.measuredWidth / 2).toInt(); val y = (cy - button.measuredHeight / 2).toInt()
            button.layout(x, y, x + button.measuredWidth, y + button.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        val c = width / 2f; val r = c - resources.displayMetrics.density
        paint.style = Paint.Style.FILL; paint.color = RemoteColors.navy; canvas.drawCircle(c, c, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 1.5f
        paint.color = RemoteColors.edge; canvas.drawCircle(c, c, r, paint)
        val inner = c * .40f
        for (angle in listOf(45, 135, 225, 315)) {
            val a = Math.toRadians(angle.toDouble()); val x = kotlin.math.cos(a).toFloat(); val y = kotlin.math.sin(a).toFloat()
            canvas.drawLine(c + x * inner, c + y * inner, c + x * r, c + y * r, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = RemoteColors.background
        canvas.drawCircle(c, c, inner, paint)
    }
    private fun target(x: Float, y: Float): Button? {
        val dx = x - width / 2f; val dy = y - height / 2f
        val distance = hypot(dx, dy)
        if (distance > width / 2f) return null
        val key = if (distance < width * .20f) 23 else if (abs(dx) > abs(dy)) { if (dx < 0) 21 else 22 } else { if (dy < 0) 19 else 20 }
        return keys[key]
    }
    // Route the whole circular segment to its native button; crossing segments cancels a hold.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touched = target(event.x, event.y)?.takeIf { it.isEnabled }
        val button = touched ?: return false
        val cancel = event.actionMasked in listOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP) && target(event.x, event.y) !== button
        val translated = MotionEvent.obtain(event)
        translated.setLocation(button.width / 2f, button.height / 2f)
        if (cancel) translated.action = MotionEvent.ACTION_CANCEL
        val handled = button.dispatchTouchEvent(translated)
        translated.recycle()
        if (cancel || event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) touched = null
        return handled
    }
}

/** Fill the viewport, shrink the pad on short phones, then allow scrolling for enlarged text. */
internal class RemoteBody(context: Context) : LinearLayout(context) {
    var pad: CircularRemotePad? = null
    var viewportHeight = 0
    init { orientation = VERTICAL }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val directionalPad = pad
        if (directionalPad != null) {
            val viewport = viewportHeight
            val maxSize = min((340 * density).toInt(), MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            directionalPad.layoutParams.width = maxSize; directionalPad.layoutParams.height = maxSize
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (viewport > 0 && measuredHeight > viewport) {
                val size = (maxSize - (measuredHeight - viewport)).coerceAtLeast(min(maxSize, (224 * density).toInt()))
                directionalPad.layoutParams.width = size; directionalPad.layoutParams.height = size
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
