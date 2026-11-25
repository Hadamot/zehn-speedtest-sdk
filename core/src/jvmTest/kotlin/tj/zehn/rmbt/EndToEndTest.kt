package tj.zehn.rmbt

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class EndToEndTest {
    @Test
    fun measuresAndSubmitsAgainstRealBackend() = runBlocking {
        val controlUrl = System.getenv("RMBT_IT_CONTROL") ?: return@runBlocking
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
