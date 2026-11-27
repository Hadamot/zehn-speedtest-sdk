package tj.zehn.rmbt.ui.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads [this] by the system bars and the display cutout on the chosen sides, keeping its own padding;
 * its background still runs under the bars (edge-to-edge).
 */
internal fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false, sides: Boolean = true) {
    val start = paddingLeft
    val end = paddingRight
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.updatePadding(
            left = start + if (sides) bars.left else 0,
            right = end + if (sides) bars.right else 0,
            top = initialTop + if (top) bars.top else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
