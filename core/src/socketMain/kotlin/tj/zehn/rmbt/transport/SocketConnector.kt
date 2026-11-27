package tj.zehn.rmbt.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import tj.zehn.rmbt.Transport

/** TCP and TLS connections; [close] releases the selector and its threads. */
internal class SocketConnector(private val selector: SelectorManager = SelectorManager()) : Connector, AutoCloseable {
    override suspend fun connect(endpoint: Endpoint): Connection {
        // Resolving the host name blocks the thread.
        val plain = withContext(Dispatchers.IO) {
            aSocket(selector).tcp().connect(endpoint.host, endpoint.port) { noDelay = true }
        }
        val socket = if (endpoint.transport == Transport.TLS) {
            try {
                plain.tls(selector.coroutineContext) { acceptAnyCertificate() }
            } catch (error: Throwable) {
                plain.close()
                throw error
            }
        } else {
            plain
        }
        return SocketConnection(plain, socket, socket.openReadChannel(), socket.openWriteChannel(autoFlush = false))
    }

    override fun close() = selector.close()
}

internal expect fun TLSConfigBuilder.acceptAnyCertificate()

private class SocketConnection(
    private val plain: Socket,
    private val socket: Socket,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
) : Connection {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        input.readAvailable(buffer, offset, length)

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) =
        output.writeFully(buffer, offset, offset + length)

    override suspend fun flush() = output.flush()

    override suspend fun close() {
        try {
            socket.close()
        } finally {
            if (plain !== socket) plain.close()
        }
    }
}
