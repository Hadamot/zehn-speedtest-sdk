package tj.zehn.rmbt.ui.util

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.inSpans
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.model.Classification
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.view.RatingMeterView
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// The data binding adapters of open-rmbt-android (ui/DataBindingAdapters.kt) used by the copied
// screens, as plain functions for view binding.

/** Live ping on the test screen, e.g. "14 ms" with a coloured icon, or a dash before it is known. */
internal fun TextView.setPing(pingNanos: Long) {
    if (pingNanos > 0) {
        val ms = pingNanos / 1_000_000.0
        val ping = if (pingNanos > 1_000_000) ms.roundToLong().toDouble() else ms
        setCompoundDrawablesWithIntrinsicBounds(getPingClassificationIcon(Classification.ofPing(ping)), 0, 0, 0)
        setTextColor(context.getColor(android.R.color.white))
        text = if (ping - ping.toInt() > 0 && ping < 10.0) {
            context.getString(R.string.rmbt_measurement_ping_value_1f, ping)
        } else {
            context.getString(R.string.rmbt_measurement_ping_value, ping.roundToInt().toString())
        }
    } else {
        setCompoundDrawablesWithIntrinsicBounds(R.drawable.rmbt_ic_small_ping_gray, 0, 0, 0)
        setTextColor(context.getColor(R.color.rmbt_text_white_transparency_40))
        text = context.getString(R.string.rmbt_measurement_dash)
    }
}

/** Download speed on the test screen. */
internal fun TextView.setDownload(speedBps: Long) =
    setLiveSpeed(speedBps, getSpeedDownloadClassification(Classification.ofDownload(speedBps / 1e6)), R.drawable.rmbt_ic_small_download_gray)

/** Upload speed on the test screen. */
internal fun TextView.setUpload(speedBps: Long) =
    setLiveSpeed(speedBps, getSpeedUploadClassificationIcon(Classification.ofUpload(speedBps / 1e6)), R.drawable.rmbt_ic_small_upload_gray)

private fun TextView.setLiveSpeed(speedBps: Long, @DrawableRes icon: Int, @DrawableRes emptyIcon: Int) {
    if (speedBps > 0) {
        setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        text = context.getString(R.string.rmbt_measurement_download_upload_speed, (speedBps / 1e6f).format())
        setTextColor(context.getColor(android.R.color.white))
    } else {
        setCompoundDrawablesWithIntrinsicBounds(emptyIcon, 0, 0, 0)
        setTextColor(context.getColor(R.color.rmbt_text_white_transparency_40))
        text = context.getString(R.string.rmbt_measurement_dash)
    }
}

/**
 * Classification icon in front of a large result value, scaled to the text height so it grows with
 * the number.
 */
internal fun TextView.setStartIconSizedToText(@DrawableRes iconRes: Int) {
    val icon = AppCompatResources.getDrawable(context, iconRes)?.mutate()
    val size = textSize.toInt()
    icon?.setBounds(0, 0, size, size)
    setCompoundDrawablesRelative(icon, null, null, null)
}

/** Hint under a result value: the coloured rating word, then what the value means when [meaning] is given. */
internal fun TextView.setResultHint(classification: Classification, meaning: String?) {
    val rating = ResultExplanations.rating(classification)
    val hint = SpannableStringBuilder()
    if (rating != null) {
        hint.inSpans(ForegroundColorSpan(ContextCompat.getColor(context, ResultExplanations.color(classification)))) {
            append(context.getString(rating))
        }
    }
    if (!meaning.isNullOrEmpty()) {
        if (hint.isNotEmpty()) hint.append(" · ")
        hint.append(meaning)
    }
    text = hint
    visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE
}

/** Overall verdict of a result ("Good connection") with its tinted card, meter and description. */
internal fun showVerdict(
    classification: Classification,
    title: TextView,
    background: View,
    meter: RatingMeterView,
    description: TextView,
) {
    val visible = if (classification == Classification.NONE) View.GONE else View.VISIBLE
    listOf(title, background, meter, description).forEach { it.visibility = visible }
    if (classification == Classification.NONE) return
    val context = title.context
    val color = ContextCompat.getColor(context, ResultExplanations.color(classification))
    title.text = ResultExplanations.verdictTitle(classification)?.let { context.getString(it) }
    title.setTextColor(color)
    background.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, VERDICT_BACKGROUND_ALPHA))
    meter.classification = classification
    description.text = ResultExplanations.verdictDescription(classification)?.let { context.getString(it) }
}

private const val VERDICT_BACKGROUND_ALPHA = 0x1F

/** Overall rating of a history entry ("Good") in its colour; hidden when not rated. */
internal fun TextView.setHistoryRating(download: Classification, upload: Classification, ping: Classification) {
    val overall = ResultExplanations.overall(download, upload, ping)
    val rating = ResultExplanations.rating(overall)
    visibility = if (rating == null) View.GONE else View.VISIBLE
    rating?.let {
        text = context.getString(it)
        setTextColor(ContextCompat.getColor(context, ResultExplanations.color(overall)))
    }
}

/** Network icon of a history entry or result: Wi-Fi bars by signal level, Ethernet, otherwise unknown. */
internal fun ImageView.setHistorySignalIcon(kind: NetworkKind, signalLevel: Int?) {
    setImageResource(
        when (kind) {
            NetworkKind.WIFI -> when (signalLevel) {
                null -> R.drawable.rmbt_ic_signal_unknown_small
                0, 1 -> R.drawable.rmbt_ic_history_wifi_1
                2 -> R.drawable.rmbt_ic_history_wifi_2
                3 -> R.drawable.rmbt_ic_history_wifi_3
                else -> R.drawable.rmbt_ic_history_wifi_4
            }
            NetworkKind.ETHERNET -> R.drawable.rmbt_ic_ethernet
            NetworkKind.CELLULAR, NetworkKind.OTHER -> R.drawable.rmbt_ic_signal_unknown_small
        }
    )
}

/** "Wi-Fi", "Ethernet", "Mobile network" or "Network". */
internal fun Context.networkKindLabel(kind: NetworkKind?): String = getString(
    when (kind) {
        NetworkKind.WIFI -> R.string.rmbt_home_wifi
        NetworkKind.ETHERNET -> R.string.rmbt_home_ethernet
        NetworkKind.CELLULAR -> R.string.rmbt_sdk_mobile_network
        NetworkKind.OTHER, null -> R.string.rmbt_sdk_other_network
    }
)
