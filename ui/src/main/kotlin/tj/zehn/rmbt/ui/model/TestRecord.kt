package tj.zehn.rmbt.ui.model

/** Kind of network a test ran on. */
internal enum class NetworkKind { WIFI, CELLULAR, ETHERNET, OTHER }

/** A point on the map; [accuracyMeters] is the radius the device is within, when known. */
internal data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
)

/** One finished test as the history and the result screen show it. */
internal data class TestRecord(
    val testUuid: String,
    val openTestUuid: String?,
    val timeMillis: Long,
    val timezone: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val serverName: String,
    val network: NetworkKind,
    val networkName: String?,
    /** Android signal level 0..4 when the test started, or null when unknown. */
    val signalLevel: Int?,
    /** Where the device was when the test started; null without the location permission or a fix. */
    val location: GeoPoint? = null,
) {
    val downloadClass: Classification get() = Classification.ofDownload(downloadMbps)
    val uploadClass: Classification get() = Classification.ofUpload(uploadMbps)
    val pingClass: Classification get() = Classification.ofPing(pingMs)
}
