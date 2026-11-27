package tj.zehn.rmbt.ui.util

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.model.Classification

/**
 * Plain-language texts for the test result: an overall verdict and a rating word for each value,
 * so the user does not have to interpret the bare numbers.
 */
internal object ResultExplanations {

    /** The connection is only as good as its weakest part, so the verdict is the lowest of the speed and ping ratings. */
    @JvmStatic
    fun overall(download: Classification?, upload: Classification?, ping: Classification?): Classification =
        listOfNotNull(download, upload, ping)
            .filter { it != Classification.NONE }
            .minByOrNull { it.intValue } ?: Classification.NONE

    @StringRes
    fun rating(classification: Classification): Int? = when (classification) {
        Classification.EXCELLENT -> R.string.rmbt_result_rating_excellent
        Classification.GOOD -> R.string.rmbt_result_rating_good
        Classification.NORMAL -> R.string.rmbt_result_rating_normal
        Classification.BAD -> R.string.rmbt_result_rating_bad
        Classification.NONE -> null
    }

    @StringRes
    fun verdictTitle(classification: Classification): Int? = when (classification) {
        Classification.EXCELLENT -> R.string.rmbt_result_verdict_excellent
        Classification.GOOD -> R.string.rmbt_result_verdict_good
        Classification.NORMAL -> R.string.rmbt_result_verdict_normal
        Classification.BAD -> R.string.rmbt_result_verdict_bad
        Classification.NONE -> null
    }

    @StringRes
    fun verdictDescription(classification: Classification): Int? = when (classification) {
        Classification.EXCELLENT -> R.string.rmbt_result_verdict_excellent_description
        Classification.GOOD -> R.string.rmbt_result_verdict_good_description
        Classification.NORMAL -> R.string.rmbt_result_verdict_normal_description
        Classification.BAD -> R.string.rmbt_result_verdict_bad_description
        Classification.NONE -> null
    }

    @ColorRes
    fun color(classification: Classification): Int = when (classification) {
        Classification.EXCELLENT -> R.color.rmbt_classification_green_dark
        Classification.GOOD -> R.color.rmbt_classification_green
        Classification.NORMAL -> R.color.rmbt_classification_yellow
        Classification.BAD -> R.color.rmbt_classification_red
        Classification.NONE -> R.color.rmbt_classification_undefined
    }

    /** Plain-language signal level for the start screen; [level] is the Android signal level 0..4. */
    @StringRes
    fun signalQuality(level: Int): Int = when {
        level >= 4 -> R.string.rmbt_home_signal_quality_excellent
        level == 3 -> R.string.rmbt_home_signal_quality_good
        level == 2 -> R.string.rmbt_home_signal_quality_fair
        else -> R.string.rmbt_home_signal_quality_weak
    }
}
