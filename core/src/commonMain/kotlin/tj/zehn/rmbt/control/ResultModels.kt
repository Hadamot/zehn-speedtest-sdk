package tj.zehn.rmbt.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ResultRequest(
    val platform: String,
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("client_name") val clientName: String = "RMBT",
    @SerialName("client_version") val clientVersion: String = "0.3",
    @SerialName("client_language") val language: String = "en",
    val time: Long,
    @SerialName("test_token") val testToken: String,
    @SerialName("test_port_remote") val portRemote: Int,
    @SerialName("test_bytes_download") val bytesDownload: Long,
    @SerialName("test_bytes_upload") val bytesUpload: Long,
    @SerialName("test_total_bytes_download") val totalBytesDownload: Long,
    @SerialName("test_total_bytes_upload") val totalBytesUpload: Long,
    @SerialName("test_encryption") val encryption: String?,
    @SerialName("test_nsec_download") val nsecDownload: Long,
    @SerialName("test_nsec_upload") val nsecUpload: Long,
    @SerialName("test_num_threads") val threads: Int,
    @SerialName("num_threads_ul") val threadsUpload: Int,
    @SerialName("test_speed_download") val speedDownloadKbps: Long,
    @SerialName("test_speed_upload") val speedUploadKbps: Long,
    @SerialName("test_ping_shortest") val pingShortestNanos: Long,
    @SerialName("network_type") val networkType: Int,
    val pings: List<PingBody>,
    @SerialName("speed_detail") val speedDetail: List<SpeedBody>,
    val tag: String? = null,
)

@Serializable
internal data class PingBody(
    val value: Long,
    @SerialName("value_server") val valueServer: Long,
    @SerialName("time_ns") val timeNs: Long,
)

@Serializable
internal data class SpeedBody(val direction: String, val thread: Int, val time: Long, val bytes: Long)

@Serializable
internal data class ErrorResponse(val error: List<String> = emptyList())
