package tj.zehn.rmbt.ui.util

import android.graphics.Paint
import android.graphics.Rect
import java.text.DecimalFormat

// Copied from open-rmbt-android (util/ViewExtensions.kt).

/** Approximate width of [demoText]. */
internal fun Paint.calcTextWidth(demoText: String): Float = measureText(demoText)

/** Approximate height of [demoText]. */
internal fun Paint.calcTextHeight(demoText: String): Float {
    val r = Rect()
    getTextBounds(demoText, 0, demoText.length, r)
    return r.height().toFloat()
}

/**
 * Speed rounding for the display: above 10 Mbit/s a whole number (123.456 -> "123"), up to 10 two
 * significant figures (5.678 -> "5.7").
 */
internal fun Float.format(): String =
    if (this > 10f) DecimalFormat("0").format(this) else DecimalFormat("@@").format(this)
