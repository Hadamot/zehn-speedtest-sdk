package tj.zehn.rmbt.ui.view

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.util.calcTextHeight
import tj.zehn.rmbt.ui.util.calcTextWidth

internal open class LineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var gridPaint: Paint
    private var yLabelPaint: Paint
    private var xLabelPaint: Paint

    private var gridDottedLinePaint: Paint
    private var gridDottedLinePath: Path

    private var startPadding: Float = 0.0f
    private var endPadding: Float = 0.0f
    private var bottomPadding: Float = 0.0f

    private var yLabels: Array<String>
    private var numberOfRows: Int = DEFAULT_NUMBER_OF_ROWS_IN_GRID

    private var xLabelMin: String
    private var xLabelMax: String

    init {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.rmbt_LineChart)
        yLabels = context.resources.getStringArray(R.array.rmbt_measurement_speed_chart_y_labels)
        xLabelMin = context.getString(R.string.rmbt_measurement_speed_chart_x_label_min)
        xLabelMax = context.getString(R.string.rmbt_measurement_speed_chart_x_label_max)

        gridPaint = Paint()
        gridPaint.color = typedArray.getColor(R.styleable.rmbt_LineChart_rmbt_grid_color, context.getColor(R.color.rmbt_chart_grid_line_color))
        gridPaint.strokeWidth = 2.0f
        gridPaint.style = Paint.Style.STROKE
        gridPaint.isAntiAlias = true

        gridDottedLinePaint = Paint()
        gridDottedLinePaint.color = typedArray.getColor(R.styleable.rmbt_LineChart_rmbt_grid_color, context.getColor(R.color.rmbt_chart_grid_line_color))
        gridDottedLinePaint.style = Paint.Style.STROKE
        gridDottedLinePaint.pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        gridDottedLinePaint.strokeWidth = 2f

        gridDottedLinePath = Path()

        val robotoRegularTypeface = ResourcesCompat.getFont(context, R.font.rmbt_roboto_regular)

        yLabelPaint = Paint()
        yLabelPaint.isAntiAlias = true
        yLabelPaint.typeface = robotoRegularTypeface
        yLabelPaint.textSize =
            typedArray.getDimension(R.styleable.rmbt_LineChart_rmbt_ylabel_text_size, context.resources.getDimension(R.dimen.rmbt_chart_label_text_size))
        yLabelPaint.textAlign = Paint.Align.LEFT
        yLabelPaint.color = typedArray.getColor(R.styleable.rmbt_LineChart_rmbt_ylabel_color, context.getColor(R.color.rmbt_chart_labels_color))

        xLabelPaint = Paint()
        xLabelPaint.isAntiAlias = true
        xLabelPaint.typeface = robotoRegularTypeface
        xLabelPaint.textSize =
            typedArray.getDimension(R.styleable.rmbt_LineChart_rmbt_xlabel_text_size, context.resources.getDimension(R.dimen.rmbt_chart_label_text_size))
        xLabelPaint.color = typedArray.getColor(R.styleable.rmbt_LineChart_rmbt_xlabel_color, context.getColor(R.color.rmbt_chart_labels_color))

        numberOfRows =
            typedArray.getInteger(R.styleable.rmbt_LineChart_rmbt_grid_row, DEFAULT_NUMBER_OF_ROWS_IN_GRID)

        typedArray.recycle()

        endPadding = yLabelPaint.calcTextWidth(DEFAULT_Y_LABEL_TEXT)
        bottomPadding = xLabelPaint.calcTextHeight(xLabelMin) + (xLabelPaint.calcTextHeight(xLabelMin) / 2)
    }

    /**
     * Return size of chart width without y labels text size
     */
    fun getChartWidth(): Float {
        return width - endPadding
    }

    /**
     * Return size of chart width without x labels text size
     */
    fun getChartHeight(): Float {
        return height - bottomPadding
    }

    fun setYLabels(yLabels: Array<String>) {
        this.yLabels = yLabels
        invalidate()
    }

    fun setXLabelMin(xLabelMin: String) {
        this.xLabelMin = xLabelMin
        invalidate()
    }

    fun setXLabelMax(xLabelMax: String) {
        this.xLabelMax = xLabelMax
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        val endX = width - endPadding
        val endY = height - bottomPadding
        val rowHeight = endY / numberOfRows

        // Draw line of grid
        for (index in 0..numberOfRows) {

            val positionY = endY - rowHeight * index
            canvas.drawLine(startPadding, positionY, endX, positionY, gridPaint)
        }

        // Draw vertical dotted line
        // A field reused every frame: start it over, or it keeps every frame's segments.
        gridDottedLinePath.rewind()
        gridDottedLinePath.moveTo(endX, 0.0f)
        gridDottedLinePath.lineTo(endX, endY)
        canvas.drawPath(gridDottedLinePath, gridDottedLinePaint)

        // Draw Y Labels text
        for (index in 0 until yLabels.size) {

            val positionY = (endY - rowHeight * index) - rowHeight + (bottomPadding)
            canvas.drawText(yLabels[index], endX + endPadding / 8, positionY, yLabelPaint)
        }

        // Draw X Labels text
        xLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(xLabelMin, startPadding, height.toFloat(), xLabelPaint)
        xLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(xLabelMax, endX, height.toFloat(), xLabelPaint)
    }

    companion object {

        public const val DEFAULT_NUMBER_OF_ROWS_IN_GRID: Int = 4
        private const val DEFAULT_Y_LABEL_TEXT: String = "100000"
    }
}