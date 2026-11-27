package tj.zehn.rmbt.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import tj.zehn.rmbt.ui.R
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One point of the live speed chart: [progress] 0..100 of the phase, [value] in bit/s. */
internal data class SpeedPoint(val progress: Int, val value: Long)

@SuppressLint("CustomViewStyleable")
internal class SpeedLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LineChart(context, attrs) {

    private var pathStroke: Path
    private var paintStroke: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pathFill: Path
    private var paintFill: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var circlePoint: PointF? = null
    private var startTime: Long = -1

    private var chartPoints: ArrayList<PointF> = ArrayList()

    private var rowCount = DEFAULT_NUMBER_OF_ROWS_IN_GRID
    init {

        context.withStyledAttributes(attrs, R.styleable.rmbt_LineChart) {
            // Access LineChart specific attributes
            rowCount = getInt(R.styleable.rmbt_LineChart_rmbt_grid_row, DEFAULT_NUMBER_OF_ROWS_IN_GRID)  // default value is 0
        }

        context.withStyledAttributes(attrs, R.styleable.rmbt_SpeedLineChart) {
            paintStroke.color = getColor(
                R.styleable.rmbt_SpeedLineChart_rmbt_progress_line_color,
                context.getColor(R.color.rmbt_colorAccent)
            )
            paintFill.color = getColor(
                R.styleable.rmbt_SpeedLineChart_rmbt_progress_fill_color,
                context.getColor(R.color.rmbt_speed_chart_progress_fill_color)
            )
        }

        paintStroke.style = Paint.Style.STROKE
        paintStroke.strokeWidth = STROKE_WIDTH
        paintStroke.strokeCap = Paint.Cap.ROUND
        paintStroke.strokeJoin = Paint.Join.ROUND
        paintStroke.isAntiAlias = true

        pathStroke = Path()

        paintFill.style = Paint.Style.FILL
        paintFill.isAntiAlias = true

        pathFill = Path()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pathStroke.isEmpty && chartPoints.isNotEmpty()) {
            calculatePath()
        }
        canvas.drawPath(pathStroke, paintStroke)
        canvas.drawPath(pathFill, paintFill)
        circlePoint?.let {
            canvas.drawCircle(
                getChartWidth() * it.x - STROKE_WIDTH / 2.0f,
                getChartHeight() - (getChartHeight() * it.y),
                STROKE_WIDTH / 2,
                paintStroke
            )
        }
    }

    private fun removeLeadingZeroValues(graphItems: List<SpeedPoint>?): List<SpeedPoint>? {
        var foundNonZero = false
        return graphItems?.filter { graphItem ->
            if (foundNonZero) {
                true
            } else if (graphItem.value != 0L) {
                foundNonZero = true
                true
            } else {
                false
            }
        }
    }

    fun addGraphItems(graphItems: List<SpeedPoint>?) {

        pathStroke.rewind()
        pathFill.rewind()

        val filteredGraphItems = removeLeadingZeroValues(graphItems)
        if (!filteredGraphItems.isNullOrEmpty()) {

            chartPoints = ArrayList()

            if (filteredGraphItems[0].progress > 0) {
                chartPoints.add(PointF(0.0f, toLog(filteredGraphItems[0].value)))
            }
            for (index in filteredGraphItems.indices) {

                val x = filteredGraphItems[index].progress / 100.0f
                val y = toLog(filteredGraphItems[index].value)

                chartPoints.add(PointF(x, y))
            }
        }
        invalidate()
    }

    /**
     * This function is use for calculate path
     */

    private fun calculatePath() {
        circlePoint = chartPoints[chartPoints.size - 1]
        var lX = 0f
        var lY = 0f
        pathStroke.moveTo(getChartWidth() * chartPoints[0].x, getChartHeight() - (getChartHeight() * chartPoints[0].y))
        for (index in 1 until chartPoints.size) {
            val currentPointX = getChartWidth() * chartPoints[index].x
            val currentPointY = getChartHeight() - (getChartHeight() * chartPoints[index].y)
            val previousPointX = getChartWidth() * chartPoints[index - 1].x
            val previousPointY = getChartHeight() - (getChartHeight() * chartPoints[index - 1].y)

            // Distance between currentPoint and previousPoint
            val firstDistance =
                sqrt((currentPointX - previousPointX).toDouble().pow(2.0) + (currentPointY - previousPointY).toDouble().pow(2.0)).toFloat()

            // Minimum is used to avoid going too much right
            val firstX = min(previousPointX + lX * firstDistance, (previousPointX + currentPointX) / 2)
            val firstY = previousPointY + lY * firstDistance

            val nextPointX = getChartWidth() * chartPoints[if (index + 1 < chartPoints.size) index + 1 else index].x
            val nextPointY = getChartHeight() - (getChartHeight() * chartPoints[if (index + 1 < chartPoints.size) index + 1 else index].y)

            // Distance between nextPoint and previousPoint (length of reference line)
            val secondDistance = sqrt((nextPointX - previousPointX).toDouble().pow(2.0) + (nextPointY - previousPointY).toDouble().pow(2.0)).toFloat()
            // (lX,lY) is the slope of the reference line
            lX = (nextPointX - previousPointX) / secondDistance * 0.3f
            lY = (nextPointY - previousPointY) / secondDistance * 0.3f

            // Maximum is used to avoid going too much left
            val secondX = max(currentPointX - lX * firstDistance, (previousPointX + currentPointX) / 2)
            val secondY = currentPointY - lY * firstDistance

            pathStroke.cubicTo(firstX, firstY, secondX, secondY, currentPointX, currentPointY)
        }

        pathFill.addPath(pathStroke)
        pathFill.lineTo(getChartWidth() * chartPoints[chartPoints.size - 1].x, getChartHeight())
        pathFill.lineTo(getChartWidth() * chartPoints[0].x, getChartHeight())
    }

    fun reset() {

        chartPoints.clear()
        pathStroke.rewind()
        pathFill.rewind()
        startTime = -1
        circlePoint = null
        invalidate()
    }

    /**
     * This function is used for convert download and upload speed into graph Y value
     */
    private fun toLog(value: Long): Float {
        return (if (value < 1e5) 0.0 else (2.0 + log10(value / 1e7)) / rowCount).toFloat()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    companion object {

        private const val STROKE_WIDTH: Float = 3.0f
    }
}