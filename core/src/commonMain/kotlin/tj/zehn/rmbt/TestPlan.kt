package tj.zehn.rmbt

import tj.zehn.rmbt.control.TestTicket
import tj.zehn.rmbt.measure.ChunkSizer
import tj.zehn.rmbt.run.TestParams
import tj.zehn.rmbt.transport.Endpoint

internal object TestPlan {
    private const val WEBSOCKET_MAX_CHUNK = 32 * 1024

    fun params(config: RmbtConfig, ticket: TestTicket.Parsed): TestParams {
        val transport = transport(config.transport, ticket.encrypted)
        return TestParams(
            endpoint = Endpoint(config.measurementHost ?: ticket.host, ticket.port, transport),
            token = ticket.token,
            threads = config.threads ?: ticket.threads,
            durationSec = config.durationSec ?: ticket.durationSec,
            pings = ticket.pings,
            httpUpgrade = ticket.httpUpgrade && !transport.isWebSocket,
            maxChunkSize = if (transport.isWebSocket) WEBSOCKET_MAX_CHUNK else ChunkSizer.MAX_CHUNK_SIZE,
        )
    }

    private fun transport(requested: Transport, encrypted: Boolean): Transport = when {
        !encrypted -> requested
        requested == Transport.TCP -> Transport.TLS
        requested == Transport.WEBSOCKET -> Transport.SECURE_WEBSOCKET
        else -> requested
    }
}
