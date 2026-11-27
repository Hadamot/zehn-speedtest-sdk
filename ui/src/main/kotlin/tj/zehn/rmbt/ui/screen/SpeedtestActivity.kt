package tj.zehn.rmbt.ui.screen

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.databinding.RmbtActivityHomeBinding

/**
 * Start screen and history with the bottom navigation, copied from open-rmbt-android (HomeActivity;
 * statistics and map are not part of the SDK). Tabs keep their state while switching.
 */
internal class SpeedtestActivity : RmbtActivity() {

    private lateinit var binding: RmbtActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        binding = RmbtActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navView.setOnItemSelectedListener { item ->
            show(if (item.itemId == R.id.rmbt_navigation_history) TAG_HISTORY else TAG_HOME)
            true
        }
        binding.navView.setOnItemReselectedListener { }
        if (savedInstanceState == null) show(TAG_HOME)
    }

    private fun show(tag: String) {
        val fm = supportFragmentManager
        fm.commit {
            setReorderingAllowed(true)
            fm.fragments.filter { it.tag != tag }.forEach { hide(it) }
            val existing = fm.findFragmentByTag(tag)
            if (existing != null) show(existing) else add(R.id.navHostFragment, create(tag), tag)
        }
    }

    private fun create(tag: String): Fragment = if (tag == TAG_HISTORY) RmbtHistoryFragment() else RmbtHomeFragment()

    private companion object {
        const val TAG_HOME = "rmbt_home"
        const val TAG_HISTORY = "rmbt_history"
    }
}
