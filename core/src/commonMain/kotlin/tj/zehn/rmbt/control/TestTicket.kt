package tj.zehn.rmbt.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TestTicket(
    // Defaults so that an error-only answer ({"error": [...]}) decodes and its message reaches the app.
    @SerialName("test_uuid") val testUuid: String = "",
    @SerialName("test_token") val token: String = "",
    @SerialName("test_server_address") val host: String = "",
    @SerialName("test_server_port") val port: Int = 0,
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
        val openTestUuid: String? = null,
    )

    fun parsed(): Parsed {
        if (testUuid.isEmpty() || token.isEmpty() || host.isEmpty() || port <= 0) {
            throw ControlServerException(listOf("incomplete test ticket"))
        }
        return toParsed()
    }

    /** The ticket's numbers come as strings; a bad one is the server's error, not a crash. */
    private fun number(name: String, value: String): Int =
        value.toIntOrNull() ?: throw ControlServerException(listOf("bad $name: $value"))

    private fun toParsed() = Parsed(
        testUuid = testUuid,
        token = token,
        host = host,
        port = port,
        encrypted = encrypted,
        httpUpgrade = serverType == "RMBThttp",
        serverName = serverName,
        durationSec = number("test_duration", duration),
        threads = number("test_numthreads", threads),
        pings = number("test_numpings", pings),
        waitSec = waitSec,
        openTestUuid = openTestUuid,
    )
}
