package tj.zehn.rmbt.control

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ControlClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        body: String,
        apiKey: String? = null,
        status: io.ktor.http.HttpStatusCode = io.ktor.http.HttpStatusCode.OK,
        contentType: ContentType = ContentType.Application.Json,
    ): ControlClient {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headers = headersOf(HttpHeaders.ContentType, contentType.toString()))
        }
        return ControlClient("http://control/RMBTControlServer", HttpClient(engine), apiKey)
    }

    private fun HttpRequestData.json() = (body as TextContent).text

    @Test
    fun registerReturnsClientUuidFromSettings() = runTest {
        val uuid = client("""{"settings":[{"uuid":"c-1"}],"error":[]}""").register(null)

        assertEquals("c-1", uuid)
        assertEquals("http://control/RMBTControlServer/settings", requests.single().url.toString())
    }

    @Test
    fun requestTestParsesTicketNumbersSentAsStrings() = runTest {
        val body = """{"test_uuid":"t-1","test_token":"t-1_1_x","test_server_address":"10.0.0.5",
            "test_server_port":8081,"test_server_encryption":false,"test_server_type":"RMBThttp",
            "test_server_name":"Local","test_duration":"7","test_numthreads":"3","test_numpings":"10",
            "test_wait":0,"open_test_uuid":"O1","error":[]}"""

        val ticket = client(body).requestTest("c-1", threads = null)

        assertEquals(TestTicket.Parsed("t-1", "t-1_1_x", "10.0.0.5", 8081, false, true, "Local", 7, 3, 10, 0, "O1"), ticket.parsed())
        assertTrue(requests.single().json().contains("\"uuid\":\"c-1\""))
    }

    @Test
    fun sendsApiKeyHeaderWhenConfigured() = runTest {
        client("""{"settings":[{"uuid":"c-1"}]}""", apiKey = "secret").register(null)

        assertEquals("secret", requests.single().headers["X-Api-Key"])
    }

    @Test
    fun requestTestReportsTheServersErrorInsteadOfAMissingField() = runTest {
        val error = assertFailsWith<ControlServerException> {
            client("""{"error":["Client is blocked"]}""").requestTest("c-1", threads = null)
        }

        assertTrue(error.message!!.contains("Client is blocked"), error.message)
    }

    @Test
    fun anIncompleteTicketIsAControlServerError() = runTest {
        val ticket = client("""{"test_uuid":"t-1","error":[]}""").requestTest("c-1", threads = null)

        assertFailsWith<ControlServerException> { ticket.parsed() }
    }

    @Test
    fun aGatewayErrorPageIsAControlServerErrorWithItsStatus() = runTest {
        val error = assertFailsWith<ControlServerException> {
            client("<html>Bad Gateway</html>", status = io.ktor.http.HttpStatusCode.BadGateway, contentType = ContentType.Text.Html)
                .register(null)
        }

        assertTrue(error.message!!.contains("502"), error.message)
    }

    @Test
    fun aRefusalWithAnErrorStatusKeepsTheServersReasons() = runTest {
        val error = assertFailsWith<ControlServerException> {
            client("""{"error":["Client is blocked"]}""", status = io.ktor.http.HttpStatusCode.Forbidden).register(null)
        }

        assertTrue(error.message!!.contains("Client is blocked"), error.message)
    }

    @Test
    fun aTicketNumberThatIsNotOneIsAControlServerError() = runTest {
        val body = """{"test_uuid":"t-1","test_token":"x","test_server_address":"h","test_server_port":1,
            "test_duration":"seven","error":[]}"""
        val ticket = client(body).requestTest("c-1", threads = null)

        assertFailsWith<ControlServerException> { ticket.parsed() }
    }

    @Test
    fun failsWhenServerReportsErrors() = runTest {
        assertFailsWith<ControlServerException> {
            client("""{"error":["test not found"]}""").submitResult(sampleResult())
        }
    }

    private fun sampleResult() = ResultRequest(
        platform = "JVM", clientUuid = "c-1", time = 1, testToken = "t", portRemote = 1,
        bytesDownload = 1, bytesUpload = 1, totalBytesDownload = 1, totalBytesUpload = 1, encryption = null,
        nsecDownload = 1, nsecUpload = 1, threads = 1, threadsUpload = 1, speedDownloadKbps = 1,
        speedUploadKbps = 1, pingShortestNanos = 1, networkType = 98, pings = emptyList(), speedDetail = emptyList(),
    )
}
