package tj.zehn.rmbt.ui.screen

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.RmbtUi
import tj.zehn.rmbt.ui.data.HistoryStore
import tj.zehn.rmbt.ui.databinding.RmbtActivityResultsBinding
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.TestRecord
import tj.zehn.rmbt.ui.util.ResultExplanations
import tj.zehn.rmbt.ui.util.ResultValueFormat
import tj.zehn.rmbt.ui.util.getPingClassificationIcon
import tj.zehn.rmbt.ui.util.getSpeedDownloadClassification
import tj.zehn.rmbt.ui.util.getSpeedUploadClassificationIcon
import tj.zehn.rmbt.ui.util.networkKindLabel
import tj.zehn.rmbt.ui.util.padForSystemBars
import tj.zehn.rmbt.ui.util.setHistorySignalIcon
import tj.zehn.rmbt.ui.util.setResultHint
import tj.zehn.rmbt.ui.util.setStartIconSizedToText
import tj.zehn.rmbt.ui.util.showVerdict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The result of a test, copied from open-rmbt-android (ResultsActivity): verdict, download, upload,
 * ping with their ratings, the network details and where the test ran. Shown after a test and from
 * the history.
 */
internal class ResultActivity : RmbtActivity() {

    private lateinit var binding: RmbtActivityResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val testUuid = intent.getStringExtra(EXTRA_TEST_UUID)
        if (testUuid == null) {
            finish()
            return
        }
        binding = RmbtActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars(top = true)
        binding.resultsScroll.padForSystemBars(bottom = true, sides = false)
        binding.buttonBack.setOnClickListener { finish() }

        // The history is read off the main thread; the screen fills in once the record is there.
        lifecycleScope.launch {
            val record = HistoryStore.get(this@ResultActivity).find(testUuid)
            if (record == null) {
                finish()
                return@launch
            }
            binding.buttonShare.setOnClickListener { share(record) }
            show(record)
        }
    }

    private fun show(record: TestRecord) = with(binding) {
        testTime.setResultTime(record.timeMillis, record.timezone)

        networkTypeText.text = networkKindLabel(record.network)
        networkNameText.text = record.networkName
        networkNameText.visibility = if (record.networkName.isNullOrEmpty()) View.GONE else View.VISIBLE
        networkTypeIcon.setHistorySignalIcon(record.network, record.signalLevel)

        showVerdict(
            ResultExplanations.overall(record.downloadClass, record.uploadClass, record.pingClass),
            textVerdict, viewVerdictBackground, meterVerdict, textVerdictDescription,
        )

        textDownloadSpeed.text = ResultValueFormat.speed(this@ResultActivity, record.downloadKbs)
        textDownloadSpeed.setStartIconSizedToText(getSpeedDownloadClassification(record.downloadClass))
        textUploadSpeed.text = ResultValueFormat.speed(this@ResultActivity, record.uploadKbs)
        textUploadSpeed.setStartIconSizedToText(getSpeedUploadClassificationIcon(record.uploadClass))
        textPing.text = ResultValueFormat.ping(this@ResultActivity, record.pingMs)
        textPing.setStartIconSizedToText(getPingClassificationIcon(record.pingClass))
        textDownloadHint.setResultHint(record.downloadClass, null)
        textUploadHint.setResultHint(record.uploadClass, null)
        textPingHint.setResultHint(record.pingClass, getString(R.string.rmbt_result_hint_ping))

        textConnection.text = networkKindLabel(record.network)
        textServer.text = record.serverName
        showLocation(record.location)
    }

    /**
     * The coordinates, and a static map with the point when the host app has a Maps API key and the
     * device has Google Play services; tapping the map opens it in a maps app.
     */
    private fun showLocation(point: GeoPoint?) = with(binding) {
        if (point == null) {
            textLocation.setText(R.string.rmbt_sdk_location_unknown)
            return
        }
        val accuracy = ResultValueFormat.accuracyMeters(point)
        textLocation.text = listOfNotNull(
            ResultValueFormat.coordinates(point),
            accuracy?.let { getString(R.string.rmbt_sdk_location_accuracy, it) },
        ).joinToString(" · ")
        if (!mapsAvailable()) return

        locationMapCard.visibility = View.VISIBLE
        locationMapCard.clipToOutline = true // Rounds the map with the card background.
        locationMap.onCreate(null) // Lite mode: the map is drawn again from the record, no state to restore.
        lifecycle.addObserver(MapViewLifecycle(locationMap))
        locationMap.getMapAsync { map ->
            val position = LatLng(point.latitude, point.longitude)
            map.uiSettings.isMapToolbarEnabled = false
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, MAP_ZOOM))
            map.addMarker(MarkerOptions().position(position))
            if (accuracy != null) {
                val accent = ContextCompat.getColor(this@ResultActivity, R.color.rmbt_colorAccent)
                map.addCircle(
                    CircleOptions()
                        .center(position)
                        .radius(accuracy.toDouble())
                        .strokeWidth(resources.displayMetrics.density * ACCURACY_STROKE_DP)
                        .strokeColor(accent)
                        .fillColor(ColorUtils.setAlphaComponent(accent, ACCURACY_FILL_ALPHA)),
                )
            }
            map.setOnMapClickListener { openInMaps(point) }
        }
    }

    /** The map needs the host app's key in its manifest; without it Google shows an empty grey map. */
    private fun mapsAvailable(): Boolean {
        val key = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
            info.metaData?.getString(MAPS_API_KEY)
        }.getOrNull()
        return !key.isNullOrBlank() &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
    }

    /** Google Maps when installed, the browser otherwise. */
    private fun openInMaps(point: GeoPoint) {
        val query = Uri.encode(ResultValueFormat.coordinates(point))
        val uri = "https://www.google.com/maps/search/?api=1&query=$query".toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // No maps app and no browser: the coordinates stay on screen and can be copied.
        }
    }

    /** Date and time with calendar and clock icons, e.g. "📅 20.11.18  🕒 12:00:45", in the test's time zone. */
    private fun TextView.setResultTime(timeMillis: Long, timezone: String) {
        val zone = TimeZone.getTimeZone(timezone)
        val date = SimpleDateFormat("dd.MM.yy", Locale.US).apply { timeZone = zone }.format(Date(timeMillis))
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = zone }.format(Date(timeMillis))
        text = SpannableStringBuilder()
            .appendIcon(this, R.drawable.rmbt_ic_calendar_small)
            .append(" ").append(date)
            .append(" ")
            .appendIcon(this, R.drawable.rmbt_ic_clock_small)
            .append(" ").append(time)
        contentDescription = "${getString(R.string.rmbt_title)} ${getString(R.string.rmbt_test_result_from)} $date, $time"
    }

    private fun SpannableStringBuilder.appendIcon(view: TextView, @DrawableRes icon: Int): SpannableStringBuilder {
        val drawable = AppCompatResources.getDrawable(view.context, icon) ?: return this
        val size = view.textSize.toInt()
        drawable.setBounds(0, 0, size, size)
        val start = length
        append(" ")
        setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    /** Shares the result as text, as open-rmbt-android does (ResultShare.text). */
    private fun share(record: TestRecord) {
        val appName = RmbtUi.config.appName ?: applicationInfo.loadLabel(packageManager).toString()
        val subject = getString(R.string.rmbt_share_result_title, appName)
        val zone = TimeZone.getTimeZone(record.timezone)
        val time = SimpleDateFormat("dd.MM.yy, HH:mm", Locale.US).apply { timeZone = zone }.format(Date(record.timeMillis))
        val verdict = ResultExplanations.overall(record.downloadClass, record.uploadClass, record.pingClass)
        val network = record.networkName ?: networkKindLabel(record.network)
        val lines = mutableListOf(
            "📊 $subject",
            "📅 $time",
            "🌐 ${getString(R.string.rmbt_network_label)}: $network",
            "",
            "⬇️ ${getString(R.string.rmbt_label_download)}: ${ResultValueFormat.speed(this, record.downloadKbs)}",
            "⬆️ ${getString(R.string.rmbt_label_upload)}: ${ResultValueFormat.speed(this, record.uploadKbs)}",
            "⏱️ ${getString(R.string.rmbt_measurement_ping)}: ${ResultValueFormat.ping(this, record.pingMs)}",
        )
        ResultExplanations.verdictTitle(verdict)?.let { title ->
            lines += ""
            lines += getString(title)
            ResultExplanations.verdictDescription(verdict)?.let { lines += getString(it) }
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
        startActivity(Intent.createChooser(send, getString(R.string.rmbt_share_result)))
    }

    companion object {
        private const val EXTRA_TEST_UUID = "tj.zehn.rmbt.ui.TEST_UUID"
        private const val MAPS_API_KEY = "com.google.android.geo.API_KEY"
        private const val MAP_ZOOM = 15f
        private const val ACCURACY_STROKE_DP = 1.5f
        private const val ACCURACY_FILL_ALPHA = 0x33

        fun start(context: Context, testUuid: String) {
            context.startActivity(Intent(context, ResultActivity::class.java).putExtra(EXTRA_TEST_UUID, testUuid))
        }
    }
}

/** A [MapView] needs the activity's lifecycle passed on to it. */
private class MapViewLifecycle(private val map: MapView) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = map.onStart()
    override fun onResume(owner: LifecycleOwner) = map.onResume()
    override fun onPause(owner: LifecycleOwner) = map.onPause()
    override fun onStop(owner: LifecycleOwner) = map.onStop()
    override fun onDestroy(owner: LifecycleOwner) = map.onDestroy()
}

private val TestRecord.downloadKbs: Long get() = (downloadMbps * 1000).toLong()
private val TestRecord.uploadKbs: Long get() = (uploadMbps * 1000).toLong()
