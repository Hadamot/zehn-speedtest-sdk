package tj.zehn.rmbt

import kotlinx.coroutines.runBlocking
import tj.zehn.rmbt.run.MeasurementRunner
import tj.zehn.rmbt.run.TestParams
import tj.zehn.rmbt.transport.Endpoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import tj.zehn.rmbt.transport.RoutingConnector
import tj.zehn.rmbt.transport.SocketConnector
import tj.zehn.rmbt.transport.WebSocketConnector
import kotlin.test.Test
import kotlin.test.assertTrue

class SocketSmokeTest {
    @Test
    fun measuresAgainstRealServer() = runBlocking {
        val token = System.getenv("RMBT_IT_TOKEN") ?: return@runBlocking
        val transport = Transport.valueOf(System.getenv("RMBT_IT_TRANSPORT") ?: "TCP")
        val connector = RoutingConnector(SocketConnector(), WebSocketConnector(HttpClient { install(WebSockets) }))
        val runner = MeasurementRunner(connector, onEvent = ::println)
        val params = TestParams(
            endpoint = Endpoint(
                host = System.getenv("RMBT_IT_HOST") ?: "localhost",
                port = System.getenv("RMBT_IT_PORT")?.toInt() ?: 18081,
                transport = transport,
            ),
            token = token,
            threads = 3,
            durationSec = 3,
            pings = 5,
            httpUpgrade = !transport.isWebSocket,
            pretestNanos = 1_000_000_000,
        )

        val measurement = runner.run(params)

        println("down=${measurement.download.mbps} up=${measurement.upload.mbps} threads=${measurement.threads}")
        assertTrue(measurement.download.mbps > 0)
    }
}
