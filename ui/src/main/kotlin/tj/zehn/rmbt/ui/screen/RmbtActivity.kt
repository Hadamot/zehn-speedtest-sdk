package tj.zehn.rmbt.ui.screen

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import tj.zehn.rmbt.ui.RmbtUi

/** Base of the SDK's screens: applies [tj.zehn.rmbt.ui.RmbtUiConfig.wrapContext], e.g. the host app's language. */
abstract class RmbtActivity internal constructor() : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RmbtUi.config.wrapContext(newBase))
    }
}
