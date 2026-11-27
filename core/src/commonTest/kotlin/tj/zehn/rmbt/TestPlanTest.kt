package tj.zehn.rmbt

import tj.zehn.rmbt.control.TestTicket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestPlanTest {
    private fun ticket(encrypted: Boolean) = TestTicket.Parsed("t", "tok", "10.0.0.5", 8081, encrypted, true, "S", 7, 3, 10, 0)

    private fun plan(transport: Transport, encrypted: Boolean) =
        TestPlan.params(RmbtConfig(controlUrl = "c", transport = transport), ticket(encrypted))

    @Test
    fun encryptionFollowsTicket() {
        assertEquals(Transport.TCP, plan(Transport.TLS, encrypted = false).endpoint.transport)
        assertEquals(Transport.SECURE_WEBSOCKET, plan(Transport.WEBSOCKET, encrypted = true).endpoint.transport)
    }

    @Test
    fun websocketSkipsHttpUpgradeAndCapsChunks() {
        val params = plan(Transport.WEBSOCKET, encrypted = false)

        assertFalse(params.httpUpgrade)
        assertEquals(32 * 1024, params.maxChunkSize)
    }

    @Test
    fun measurementHostOverridesTicket() {
        val config = RmbtConfig(controlUrl = "c", transport = Transport.TCP, measurementHost = "localhost")

        assertEquals("localhost", TestPlan.params(config, ticket(false)).endpoint.host)
    }

    @Test
    fun shortConfigConstructorUsesPlatformDefaults() {
        val config = RmbtConfig("http://c", "key")

        assertEquals(Platform.defaultTransport, config.transport)
        assertEquals("key", config.apiKey)
    }

    @Test
    fun defaultsToTojiktelecomControlServer() {
        assertEquals("https://speedtest.tojiktelecom.tj/RMBTControlServer", RmbtConfig().controlUrl)
        assertEquals(RmbtConfig.DEFAULT_CONTROL_URL, RmbtConfig(apiKey = "k").controlUrl)
    }

    @Test
    fun theApiKeyStaysOutOfTheConfigsText() {
        val text = RmbtConfig(controlUrl = "https://c", apiKey = "secret-key").toString()

        kotlin.test.assertFalse("secret-key" in text, text)
        kotlin.test.assertTrue("apiKey=***" in text, text)
    }
}
