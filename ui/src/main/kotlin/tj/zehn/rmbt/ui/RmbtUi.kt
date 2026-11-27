package tj.zehn.rmbt.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import tj.zehn.rmbt.ui.screen.RmbtHistoryActivity
import tj.zehn.rmbt.RmbtConfig
import tj.zehn.rmbt.ui.screen.SpeedtestActivity

/**
 * Settings of the ready-made screens.
 *
 * @param controlUrl Control Server base URL; the Tojiktelecom backend by default.
 * @param apiKey sent as `X-Api-Key`, identifies the host app at a gateway.
 * @param tag free label stored with every test on the server.
 * @param appName shown in the shared result ("<appName> speed test result"); the host app's label by default.
 * @param requestLocationPermission ask for the location on "Start" when the host app declares
 *   `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` in its manifest and holds neither; the test then
 *   runs whatever the answer. With the permission the result shows where the test ran on a map (the host
 *   app also sets its Maps API key, `com.google.android.geo.API_KEY`). Turn off when the host asks itself.
 * @param wrapContext applied to the base context of every SDK screen, e.g. to show it in the language the
 *   host app chose itself (`Context.createConfigurationContext` with the app's locale). Unchanged by default.
 */
data class RmbtUiConfig(
    val controlUrl: String = RmbtConfig.DEFAULT_CONTROL_URL,
    val apiKey: String? = null,
    val tag: String? = null,
    val appName: String? = null,
    val requestLocationPermission: Boolean = true,
    val wrapContext: (Context) -> Context = { it },
)

/**
 * Entry point of the speed test screens copied from open-rmbt-android: start screen, running test,
 * result and history.
 *
 * ```
 * RmbtUi.configure(RmbtUiConfig(apiKey = "my-app"))   // optional
 * RmbtUi.start(context)
 * ```
 *
 * The screens can also be embedded: [tj.zehn.rmbt.ui.screen.RmbtHomeFragment] and
 * [tj.zehn.rmbt.ui.screen.RmbtHistoryFragment].
 */
object RmbtUi {

    @Volatile
    var config: RmbtUiConfig = RmbtUiConfig()
        private set

    fun configure(config: RmbtUiConfig) {
        this.config = config
    }

    /** Opens the start screen with the bottom navigation (start screen, history). */
    fun start(context: Context) {
        context.startActivity(intent(context))
    }

    /** Opens the test history as a screen of its own, with a back arrow. */
    fun startHistory(context: Context) {
        context.startActivity(historyIntent(context))
    }

    /** Intent of the test history screen. */
    fun historyIntent(context: Context): Intent = Intent(context, RmbtHistoryActivity::class.java).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Intent of the start screen, e.g. for a launcher shortcut or a notification. */
    fun intent(context: Context): Intent = Intent(context, SpeedtestActivity::class.java).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
