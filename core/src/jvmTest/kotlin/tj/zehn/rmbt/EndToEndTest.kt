package tj.zehn.rmbt

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class EndToEndTest {
    @Test
    fun measuresAndSubmitsAgainstRealBackend() = runBlocking {
        // Skipped (reported as such, not as passed) without a backend.
        val controlUrl: String = System.getenv("RMBT_IT_CONTROL") ?: run {
            org.junit.Assume.assumeTrue("RMBT_IT_CONTROL is not set", false)
            return@runBlocking
        }
        val config = RmbtConfig(
            controlUrl = controlUrl,
            transport = Transport.valueOf(System.getenv("RMBT_IT_TRANSPORT") ?: "TCP"),
            measurementHost = System.getenv("RMBT_IT_HOST"),
            durationSec = 3,
            tag = "sdk-e2e",
        )

        val result = RmbtClient(config).run { println(it) }

        println(result)
        assertTrue(result.downloadMbps > 0 && result.uploadMbps > 0)
    }
}
