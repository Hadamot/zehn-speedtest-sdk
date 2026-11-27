package tj.zehn.rmbt.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.util.ResultExplanations
import tj.zehn.rmbt.ui.model.Classification

/**
 * Four horizontal segments, one per rating step (bad, fair, good, excellent). The segments up to the
 * current [classification] are filled in its color, so the user sees at a glance how far the result
 * is from the best rating. The fill grows in when the classification changes.
 */
internal class RatingMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val gap = 4 * density
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.rmbt_rating_meter_empty)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** 0..SEGMENTS, animated towards the number of filled segments. */
    private var filled = 0f
    private var animator: ValueAnimator? = null

    var classification: Classification = Classification.NONE
        set(value) {
            if (field == value) return
            field = value
            fillPaint.color = ContextCompat.getColor(context, ResultExplanations.color(value))
            contentDescription = ResultExplanations.rating(value)?.let { context.getString(it) }
            animateTo(value.level().toFloat())
        }

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (!(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) || !isLaidOut) {
            filled = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, target).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                filled = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize((6 * density).toInt(), heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val segmentWidth = (width - paddingLeft - paddingRight - gap * (SEGMENTS - 1)) / SEGMENTS
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val radius = (bottom - top) / 2
        for (i in 0 until SEGMENTS) {
            val left = paddingLeft + i * (segmentWidth + gap)
            rect.set(left, top, left + segmentWidth, bottom)
            canvas.drawRoundRect(rect, radius, radius, emptyPaint)
            val fraction = (filled - i).coerceIn(0f, 1f)
            if (fraction > 0f) {
                rect.right = left + segmentWidth * fraction
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun Classification.level(): Int = when (this) {
        Classification.BAD -> 1
        Classification.NORMAL -> 2
        Classification.GOOD -> 3
        Classification.EXCELLENT -> 4
        Classification.NONE -> 0
    }

    private companion object {
        const val SEGMENTS = 4
    }
}
