package tj.zehn.rmbt.ui.util

import android.content.Context
import androidx.core.os.ConfigurationCompat
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.model.GeoPoint
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Speed and ping values as shown on the result screen and in the shared result, so both read the
 * same. Numbers use the decimal separator of the language selected in the app (e.g. "23,9" in Russian).
 */
internal object ResultValueFormat {

    /** e.g. "38.55980, 68.78700": always a dot and five decimals (about a metre), as maps apps take them. */
    fun coordinates(point: GeoPoint): String =
        String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)

    /** The accuracy radius in whole metres, or null when unknown or below a metre. */
    fun accuracyMeters(point: GeoPoint): Int? = point.accuracyMeters?.roundToInt()?.takeIf { it > 0 }

    /** e.g. "23,9 Mbit/s" (one decimal), or a dash when nothing was measured. */
    fun speed(context: Context, speedKbs: Long): String =
        if (speedKbs > 0) {
            context.getString(R.string.rmbt_measurement_download_upload_speed, speedNumber(context, speedKbs))
        } else {
            context.getString(R.string.rmbt_measurement_dash)
        }

    /** e.g. "14 ms", or "4,5 ms" below 10 ms, or a dash when nothing was measured. */
    fun ping(context: Context, pingMillis: Double): String =
        if (pingMillis > 0) {
            context.getString(R.string.rmbt_measurement_ping_value, pingNumber(context, pingMillis))
        } else {
            context.getString(R.string.rmbt_measurement_dash)
        }

    /** The speed in Mbit/s without the unit, e.g. "23,9"; a dash when nothing was measured. */
    fun speedNumber(context: Context, speedKbs: Long): String =
        if (speedKbs > 0) {
            speedNumber(speedKbs, locale(context))
        } else {
            context.getString(R.string.rmbt_measurement_dash)
        }

    /** The ping in ms without the unit, e.g. "14" or "4,5" below 10 ms; a dash when nothing was measured. */
    fun pingNumber(context: Context, pingMillis: Double): String =
        if (pingMillis > 0) {
            pingNumber(pingMillis, locale(context))
        } else {
            context.getString(R.string.rmbt_measurement_dash)
        }

    /** A measured speed (> 0 kbit/s) in Mbit/s with one decimal, rounded half up, e.g. "23,9" for [locale] ru. */
    internal fun speedNumber(speedKbs: Long, locale: Locale): String =
        number(locale, "0.0").format(speedKbs / 1000.0)

    /** A measured ping (> 0 ms): one decimal below 10 ms when it has a fraction, e.g. "4,5", else whole, e.g. "14". */
    internal fun pingNumber(pingMillis: Double, locale: Locale): String {
        val hasFraction = pingMillis - pingMillis.toInt() > 0
        val pattern = if (hasFraction && pingMillis < 10.0) "0.0" else "0"
        return number(locale, pattern).format(pingMillis)
    }

    /**
     * A value the server formatted itself, e.g. "14 Mbps" or "31 ms" on a map point, with the unit in
     * the app language. The server answers in English for languages it doesn't know (such as Tajik).
     */
    @JvmStatic
    fun serverValue(context: Context, value: String?): String? = serverValue(
        value,
        // A word joiner after the slash keeps "Мбит/с" whole when it wraps under the number.
        speedUnit = context.getString(R.string.rmbt_speed_progress_units).replace("/", "/\u2060"),
        pingUnit = context.getString(R.string.rmbt_map_legend_unit_ms)
    )

    /** The server's network type, e.g. "4G (LTE)"; its "WLAN" reads as on the home screen ("Wi-Fi" in Russian and Tajik). */
    @JvmStatic
    fun networkType(context: Context, type: String?): String? =
        if (type.equals("WLAN", ignoreCase = true)) context.getString(R.string.rmbt_home_wifi) else type

    internal fun serverValue(value: String?, speedUnit: String, pingUnit: String): String? {
        val match = value?.let { SERVER_VALUE.matchEntire(it.trim()) } ?: return value
        val unit = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "mbps", "mbit/s" -> speedUnit
            "ms" -> pingUnit
            else -> return value
        }
        return match.groupValues[1] + " " + unit
    }

    private val SERVER_VALUE = Regex("""([-\d.,]+)\s*([A-Za-z/]+)""")

    /** The language the app shows, for its decimal separator. */
    private fun locale(context: Context): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    private fun number(locale: Locale, pattern: String): DecimalFormat =
        DecimalFormat(pattern, DecimalFormatSymbols(locale)).apply { roundingMode = RoundingMode.HALF_UP }
}
