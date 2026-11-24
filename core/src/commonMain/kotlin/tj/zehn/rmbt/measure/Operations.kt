package tj.zehn.rmbt.measure

import kotlinx.coroutines.withTimeoutOrNull
import tj.zehn.rmbt.protocol.ClientCommand
import tj.zehn.rmbt.protocol.RmbtSocket
import tj.zehn.rmbt.protocol.ServerMessage

private const val DOWNLOAD_GRACE_MS = 3_000L

suspend fun RmbtSocket.getChunks(chunks: Int, chunkSize: Int): Long {
    expect<ServerMessage.Accept>()
    send(ClientCommand.GetChunks(chunks, chunkSize))
    receiveChunks(chunkSize) {}
    send(ClientCommand.Ok)
    return expect<ServerMessage.Time>().nanos
}

suspend fun RmbtSocket.ping(clock: Clock): PingSample {
    expect<ServerMessage.Accept>()
    val start = clock.nanos()
    send(ClientCommand.Ping)
    expect<ServerMessage.Pong>()
    val clientNanos = clock.nanos() - start
    send(ClientCommand.Ok)
    return PingSample(clientNanos, expect<ServerMessage.Time>().nanos, start)
}

suspend fun RmbtSocket.download(
    seconds: Int,
    chunkSize: Int,
    clock: Clock,
    start: Long,
    onBytes: (Long) -> Unit,
): Transfer {
    expect<ServerMessage.Accept>()
    send(ClientCommand.GetTime(seconds, chunkSize))
    val samples = ArrayList<Sample>()
    val received = withTimeoutOrNull(seconds * 1_000L + DOWNLOAD_GRACE_MS) {
        receiveChunks(chunkSize) { total ->
            samples += Sample(clock.nanos() - start, total)
            onBytes(total)
        }
    }
    if (received == null) return Transfer(samples, completed = false)
    send(ClientCommand.Ok)
    expect<ServerMessage.Time>()
    return Transfer(samples, completed = true)
}

suspend fun RmbtSocket.putNoResult(chunks: Int, chunk: ByteArray) {
    expect<ServerMessage.Accept>()
    send(ClientCommand.PutNoResult(chunk.size))
    expect<ServerMessage.Ok>()
    repeat(chunks) { sendChunk(chunk, last = it == chunks - 1) }
    expect<ServerMessage.Time>()
}
