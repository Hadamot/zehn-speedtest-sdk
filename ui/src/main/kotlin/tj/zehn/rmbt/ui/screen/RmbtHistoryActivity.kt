package tj.zehn.rmbt.ui.screen

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.util.padForSystemBars

/** The test history as a screen of its own, with a back arrow (see [tj.zehn.rmbt.ui.RmbtUi.startHistory]). */
internal class RmbtHistoryActivity : RmbtActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = FragmentContainerView(this).apply { id = R.id.rmbt_history_container }
        setContentView(container)
        container.padForSystemBars(bottom = true, sides = false)
        if (savedInstanceState == null) {
            supportFragmentManager.commit { add(R.id.rmbt_history_container, RmbtHistoryFragment.withBackButton()) }
        }
    }
}
