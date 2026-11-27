package tj.zehn.rmbt.ui.util

import androidx.annotation.DrawableRes
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.model.Classification

// Icon mappings copied from open-rmbt-android (ui/DataBindingAdapters.kt).

@DrawableRes
internal fun getBigDownloadIconAccordingToSpeed(downloadSpeedBps: Long): Int =
    when (Classification.ofDownload(downloadSpeedBps / 1e6)) {
        Classification.NONE, Classification.BAD -> R.drawable.rmbt_ic_speed_download_red
        Classification.NORMAL -> R.drawable.rmbt_ic_speed_download_yellow
        Classification.GOOD -> R.drawable.rmbt_ic_speed_download_light_green
        Classification.EXCELLENT -> R.drawable.rmbt_ic_speed_download_dark_green
    }

@DrawableRes
internal fun getBigUploadIconAccordingToSpeed(uploadSpeedBps: Long): Int =
    when (Classification.ofUpload(uploadSpeedBps / 1e6)) {
        Classification.NONE, Classification.BAD -> R.drawable.rmbt_ic_speed_upload_red
        Classification.NORMAL -> R.drawable.rmbt_ic_speed_upload_yellow
        Classification.GOOD -> R.drawable.rmbt_ic_speed_upload_light_green
        Classification.EXCELLENT -> R.drawable.rmbt_ic_speed_upload_dark_green
    }

@DrawableRes
internal fun getSpeedDownloadClassification(classification: Classification): Int = when (classification) {
    Classification.NONE -> R.drawable.rmbt_ic_small_download_gray
    Classification.BAD -> R.drawable.rmbt_ic_small_download_red
    Classification.NORMAL -> R.drawable.rmbt_ic_small_download_yellow
    Classification.GOOD -> R.drawable.rmbt_ic_small_download_light_green
    Classification.EXCELLENT -> R.drawable.rmbt_ic_small_download_dark_green
}

@DrawableRes
internal fun getSpeedUploadClassificationIcon(classification: Classification): Int = when (classification) {
    Classification.NONE -> R.drawable.rmbt_ic_small_upload_gray
    Classification.BAD -> R.drawable.rmbt_ic_small_upload_red
    Classification.NORMAL -> R.drawable.rmbt_ic_small_upload_yellow
    Classification.GOOD -> R.drawable.rmbt_ic_small_upload_light_green
    Classification.EXCELLENT -> R.drawable.rmbt_ic_small_upload_dark_green
}

@DrawableRes
internal fun getPingClassificationIcon(classification: Classification): Int = when (classification) {
    Classification.NONE -> R.drawable.rmbt_ic_small_ping_gray
    Classification.BAD -> R.drawable.rmbt_ic_small_ping_red
    Classification.NORMAL -> R.drawable.rmbt_ic_small_ping_yellow
    Classification.GOOD -> R.drawable.rmbt_ic_small_ping_light_green
    Classification.EXCELLENT -> R.drawable.rmbt_ic_small_ping_dark_green
}
