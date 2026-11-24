package tj.zehn.rmbt

import kotlinx.coroutines.runBlocking
import tj.zehn.rmbt.run.MeasurementRunner
import tj.zehn.rmbt.run.TestParams
import tj.zehn.rmbt.transport.Endpoint
import tj.zehn.rmbt.transport.SocketConnector
import kotlin.test.Test
import kotlin.test.assertTrue

class SocketSmokeTest {
    @Test
    fun measuresAgainstRealServer() = runBlocking {
        val token = System.getenv("RMBT_IT_TOKEN") ?: return@runBlocking
        val runner = MeasurementRunner(SocketConnector(), onEvent = ::println)
        val params = TestParams(
            endpoint = Endpoint(
                host = System.getenv("RMBT_IT_HOST") ?: "localhost",
                port = System.getenv("RMBT_IT_PORT")?.toInt() ?: 18081,
                transport = Transport.valueOf(System.getenv("RMBT_IT_TRANSPORT") ?: "TCP"),
            ),
            token = token,
            threads = 3,
            durationSec = 3,
            pings = 5,
            httpUpgrade = true,
            pretestNanos = 1_000_000_000,
        )

        val measurement = runner.run(params)

        println("down=${measurement.download.mbps} up=${measurement.upload.mbps} threads=${measurement.threads}")
        assertTrue(measurement.download.mbps > 0)
    }
}
