package tj.zehn.rmbt.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.databinding.RmbtViewLastTestCardBinding
import tj.zehn.rmbt.ui.databinding.RmbtViewLastTestMetricBinding
import tj.zehn.rmbt.ui.util.getPingClassificationIcon
import tj.zehn.rmbt.ui.util.getSpeedDownloadClassification
import tj.zehn.rmbt.ui.util.getSpeedUploadClassificationIcon
import tj.zehn.rmbt.ui.model.LastTestCard
import tj.zehn.rmbt.ui.model.LastTestSummary
import tj.zehn.rmbt.ui.util.HistoryTimeFormat
import tj.zehn.rmbt.ui.util.ResultExplanations
import tj.zehn.rmbt.ui.model.Classification

/**
 * The start screen's card under the wave: when and how fast the user's most recent test was.
 * Renders a [LastTestCard] only; what happens on a tap is set by the screen with [setOnClickListener].
 */
internal class LastTestCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = RmbtViewLastTestCardBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.rmbt_bg_last_test_card)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.rmbt_home_last_test_card_min_height)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        show(LastTestCard.Hidden)
    }

    private var stacked = false

    /**
     * Time and values share one row. When a number would not fit there (large font size, long
     * value, narrow screen), the time goes above the values so the three numbers get the whole
     * width instead of being clipped.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!stacked && valuesClipped()) {
            setStacked(true)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun valuesClipped(): Boolean = listOf(binding.download, binding.upload, binding.ping).any { metric ->
        listOf(metric.tvName, metric.tvValue, metric.tvUnit).any { tv ->
            tv.paint.measureText(tv.text.toString()) + tv.totalPaddingLeft + tv.totalPaddingRight > tv.measuredWidth + 1
        }
    }

    private fun setStacked(stacked: Boolean) {
        this.stacked = stacked
        binding.llContent.orientation = if (stacked) VERTICAL else HORIZONTAL
        binding.llContent.gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL
        (binding.llTime.layoutParams as LayoutParams).apply {
            marginEnd = if (stacked) 0 else resources.getDimensionPixelSize(R.dimen.rmbt_padding_medium_large)
            bottomMargin = if (stacked) resources.getDimensionPixelSize(R.dimen.rmbt_margin_medium) else 0
        }
        (binding.llMetrics.layoutParams as LayoutParams).apply {
            width = if (stacked) LayoutParams.MATCH_PARENT else 0
            weight = if (stacked) 0f else 1f
        }
    }

    fun show(card: LastTestCard) {
        visibility = if (card == LastTestCard.Hidden) View.GONE else View.VISIBLE
        when (card) {
            LastTestCard.Hidden -> Unit
            LastTestCard.NoTests -> {
                binding.llResult.visibility = View.GONE
                binding.vRatingStripe.visibility = View.GONE
                binding.tvNoTests.visibility = View.VISIBLE
                isClickable = false
                isFocusable = false
                contentDescription = binding.tvNoTests.text
            }
            is LastTestCard.Result -> showResult(card.summary)
        }
    }

    private fun showResult(summary: LastTestSummary) {
        // New values: try the single row again, onMeasure stacks it if they do not fit.
        if (stacked) setStacked(false)
        binding.llResult.visibility = View.VISIBLE
        binding.tvNoTests.visibility = View.GONE
        isClickable = true
        isFocusable = true

        val time = HistoryTimeFormat.format(
            summary.timeMillis,
            summary.timezone,
            System.currentTimeMillis(),
            context.getString(R.string.rmbt_history_today),
            context.getString(R.string.rmbt_history_yesterday)
        )
        binding.tvTime.text = time

        val rating = summary.rating
        binding.vRatingStripe.visibility = if (rating == Classification.NONE) View.INVISIBLE else View.VISIBLE
        binding.vRatingStripe.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, ResultExplanations.color(rating)))

        val mbps = context.getString(R.string.rmbt_speed_progress_units)
        val ms = context.getString(R.string.rmbt_history_item_header_ms)
        binding.download.show(summary.download, R.string.rmbt_measurement_download, getSpeedDownloadClassification(summary.download.classification), mbps)
        binding.upload.show(summary.upload, R.string.rmbt_measurement_upload, getSpeedUploadClassificationIcon(summary.upload.classification), mbps)
        binding.ping.show(summary.ping, R.string.rmbt_measurement_ping, getPingClassificationIcon(summary.ping.classification), ms)

        // Read as one element by TalkBack: "Last test, Today, 09:10, Download 45.2 Mbit/s, …, Good".
        contentDescription = listOfNotNull(
            context.getString(R.string.rmbt_home_last_test_title),
            time,
            "${context.getString(R.string.rmbt_measurement_download)} ${summary.download.text} $mbps",
            "${context.getString(R.string.rmbt_measurement_upload)} ${summary.upload.text} $mbps",
            "${context.getString(R.string.rmbt_measurement_ping)} ${summary.ping.text} $ms",
            ResultExplanations.rating(rating)?.let { context.getString(it) },
        ).joinToString(", ")
    }

    private fun RmbtViewLastTestMetricBinding.show(
        value: LastTestSummary.Value,
        @StringRes name: Int,
        @DrawableRes icon: Int,
        unit: String
    ) {
        tvName.setText(name)
        tvValue.text = value.text
        tvValue.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        tvUnit.text = unit
    }
}
