package tj.zehn.rmbt.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import tj.zehn.rmbt.Transport

internal class WebSocketConnector(private val client: HttpClient) : Connector {
    override suspend fun connect(endpoint: Endpoint): Connection {
        val session = client.webSocketSession {
            url {
                protocol = if (endpoint.transport == Transport.SECURE_WEBSOCKET) URLProtocol.WSS else URLProtocol.WS
                host = endpoint.host
                port = endpoint.port
                path("rmbt")
            }
        }
        return WebSocketConnection(session)
    }
}

private class WebSocketConnection(private val session: DefaultClientWebSocketSession) : Connection {
    private var frame = ByteArray(0)
    private var position = 0
    private var unflushed = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (position == frame.size) {
            val received = session.incoming.receiveCatching()
            // A network failure closes the channel with its cause: report that, not a plain end of stream.
            received.exceptionOrNull()?.let { throw it }
            val next = received.getOrNull() ?: return -1
            if (next is Frame.Binary || next is Frame.Text) {
                frame = next.data
                position = 0
            }
        }
        val count = minOf(length, frame.size - position)
        frame.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
        session.send(Frame.Binary(true, buffer.copyOfRange(offset, offset + length)))
        unflushed += length
        if (unflushed >= FLUSH_THRESHOLD) flush()
    }

    override suspend fun flush() {
        session.flush()
        unflushed = 0
    }

    override suspend fun close() = session.close()

    private companion object {
        const val FLUSH_THRESHOLD = 1 shl 20
    }
}
