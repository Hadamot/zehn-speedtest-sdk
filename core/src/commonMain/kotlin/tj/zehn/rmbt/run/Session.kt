package tj.zehn.rmbt.run

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tj.zehn.rmbt.protocol.RmbtSocket
import tj.zehn.rmbt.protocol.ServerMessage
import tj.zehn.rmbt.protocol.handshake
import tj.zehn.rmbt.transport.Connector

internal class Session(val socket: RmbtSocket, val limits: ServerMessage.ChunkSize)

/** Connects and shakes hands; a failed handshake (wrong token, dropped connection) closes the connection. */
internal suspend fun Connector.open(params: TestParams): Session {
    val socket = RmbtSocket(connect(params.endpoint))
    return try {
        Session(socket, socket.handshake(params.token, params.httpUpgrade))
    } catch (error: Throwable) {
        withContext(NonCancellable) { runCatching { socket.close() } }
        throw error
    }
}

internal suspend fun Session.close() = withContext(NonCancellable) { runCatching { socket.close() } }

internal suspend fun <T, R> List<T>.parallel(block: suspend (Int, T) -> R): List<R> = coroutineScope {
    mapIndexed { index, item -> async { block(index, item) } }.awaitAll()
}
