package tj.zehn.rmbt.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TestTicket(
    @SerialName("test_uuid") val testUuid: String,
    @SerialName("test_token") val token: String,
    @SerialName("test_server_address") val host: String,
    @SerialName("test_server_port") val port: Int,
    @SerialName("test_server_encryption") val encrypted: Boolean = false,
    @SerialName("test_server_type") val serverType: String = "RMBT",
    @SerialName("test_server_name") val serverName: String = "",
    @SerialName("test_duration") val duration: String = "7",
    @SerialName("test_numthreads") val threads: String = "3",
    @SerialName("test_numpings") val pings: String = "10",
    @SerialName("test_wait") val waitSec: Int = 0,
    @SerialName("open_test_uuid") val openTestUuid: String? = null,
    val error: List<String> = emptyList(),
) {
    data class Parsed(
        val testUuid: String,
        val token: String,
        val host: String,
        val port: Int,
        val encrypted: Boolean,
        val httpUpgrade: Boolean,
        val serverName: String,
        val durationSec: Int,
        val threads: Int,
        val pings: Int,
        val waitSec: Int,
    )

    fun parsed() = Parsed(
        testUuid = testUuid,
        token = token,
        host = host,
        port = port,
        encrypted = encrypted,
        httpUpgrade = serverType == "RMBThttp",
        serverName = serverName,
        durationSec = duration.toInt(),
        threads = threads.toInt(),
        pings = pings.toInt(),
        waitSec = waitSec,
    )
}
