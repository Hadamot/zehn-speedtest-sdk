package tj.zehn.rmbt.ui.screen

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import tj.zehn.rmbt.ui.R

/**
 * Base of the SDK's embeddable screens. They inflate with the SDK theme, so they work in any host
 * activity (also a non-AppCompat one, as in Compose apps), and they give the status bar back to the
 * host as it was once they are paused or hidden.
 */
abstract class RmbtFragment internal constructor() : Fragment() {

    /** The host's status bar icon style before this screen changed it; null while not changed. */
    private var hostLightStatusBar: Boolean? = null

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        return inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.rmbt_AppTheme))
    }

    override fun onPause() {
        super.onPause()
        restoreStatusBar()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) restoreStatusBar()
    }

    /** Dark status bar icons on a light screen, light icons on a dark or coloured one. */
    internal fun setLightStatusBar(light: Boolean) {
        val window = activity?.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hostLightStatusBar == null) hostLightStatusBar = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = light
    }

    private fun restoreStatusBar() {
        val host = hostLightStatusBar ?: return
        hostLightStatusBar = null
        val window = activity?.window ?: return
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = host
    }
}
