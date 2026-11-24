package tj.zehn.rmbt.web

import tj.zehn.rmbt.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RmbtWebOptionsTest {
    @Test
    fun mapsGivenOptionsToConfig() {
        val options = RmbtWebOptions("http://c", apiKey = "k", threads = 2, tag = "web")

        val config = options.toConfig()

        assertEquals("http://c", config.controlUrl)
        assertEquals("k", config.apiKey)
        assertEquals(2, config.threads)
        assertEquals("web", config.tag)
        assertEquals(Transport.WEBSOCKET, config.transport)
    }

    @Test
    fun leavesMissingOptionsUnset() {
        val options = RmbtWebOptions("http://c")

        val config = options.toConfig()

        assertNull(config.apiKey)
        assertNull(config.durationSec)
    }
}
