package tj.zehn.rmbt.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import tj.zehn.rmbt.Transport
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class SocketConnector(private val selector: SelectorManager = SelectorManager(Dispatchers.IO)) : Connector {
    override suspend fun connect(endpoint: Endpoint): Connection {
        val plain = aSocket(selector).tcp().connect(endpoint.host, endpoint.port) { noDelay = true }
        val socket = if (endpoint.transport == Transport.TLS) plain.tls(Dispatchers.IO) { trustManager = TrustAll } else plain
        return SocketConnection(socket, socket.openReadChannel(), socket.openWriteChannel(autoFlush = false))
    }
}

private class SocketConnection(
    private val socket: Socket,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
) : Connection {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        input.readAvailable(buffer, offset, length)

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) =
        output.writeFully(buffer, offset, offset + length)

    override suspend fun flush() = output.flush()

    override suspend fun close() = socket.close()
}

private object TrustAll : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
