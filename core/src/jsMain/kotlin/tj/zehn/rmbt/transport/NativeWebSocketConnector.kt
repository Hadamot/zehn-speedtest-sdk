package tj.zehn.rmbt.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.ARRAYBUFFER
import org.w3c.dom.BinaryType
import org.w3c.dom.WebSocket
import tj.zehn.rmbt.Transport
import tj.zehn.rmbt.protocol.RmbtException

class NativeWebSocketConnector : Connector {
    override suspend fun connect(endpoint: Endpoint): Connection {
        val scheme = if (endpoint.transport == Transport.SECURE_WEBSOCKET) "wss" else "ws"
        val socket = openWebSocket("$scheme://${endpoint.host}:${endpoint.port}/rmbt")
        socket.binaryType = BinaryType.ARRAYBUFFER
        val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Unit>()
        socket.onopen = { opened.complete(Unit) }
        socket.onmessage = { event -> incoming.trySend(bytesOf(event.data)) }
        socket.onerror = { opened.completeExceptionally(RmbtException("websocket error")) }
        socket.onclose = {
            incoming.close()
            opened.completeExceptionally(RmbtException("websocket closed"))
        }
        opened.await()
        return NativeWebSocketConnection(socket, incoming)
    }

    private fun openWebSocket(url: String): WebSocket {
        val constructor: dynamic = if (js("typeof window !== 'undefined'") as Boolean) js("WebSocket") else js("require('ws')")
        return js("new constructor(url)").unsafeCast<WebSocket>()
    }

    private fun bytesOf(data: Any?): ByteArray =
        if (data is String) data.encodeToByteArray() else Int8Array(data as ArrayBuffer).unsafeCast<ByteArray>()
}

private class NativeWebSocketConnection(
    private val socket: WebSocket,
    private val incoming: Channel<ByteArray>,
) : Connection {
    private var frame = ByteArray(0)
    private var position = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (position == frame.size) {
            frame = incoming.receiveCatching().getOrNull() ?: return -1
            position = 0
        }
        val count = minOf(length, frame.size - position)
        frame.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
        socket.send(buffer.copyOfRange(offset, offset + length).unsafeCast<Int8Array>())
        while (socket.bufferedAmount.toInt() > MAX_BUFFERED_BYTES) delay(1)
    }

    override suspend fun flush() = Unit

    override suspend fun close() = socket.close()

    private companion object {
        const val MAX_BUFFERED_BYTES = 4 shl 20
    }
}
