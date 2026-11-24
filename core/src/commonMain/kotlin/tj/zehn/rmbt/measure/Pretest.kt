package tj.zehn.rmbt.measure

import tj.zehn.rmbt.protocol.RmbtSocket
import tj.zehn.rmbt.protocol.ServerMessage

data class Pretest(val chunks: Int, val chunkSize: Int)

suspend fun RmbtSocket.pretestDownload(limits: ServerMessage.ChunkSize, durationNanos: Long, clock: Clock): Pretest {
    val end = clock.nanos() + durationNanos
    var chunks = 1
    var rttNanos = 0L
    var lastNanos = 0L
    var lastBytes = 0L
    do {
        val nanos = getChunks(chunks, limits.size)
        if (rttNanos == 0L) rttNanos = nanos
        lastNanos = nanos
        lastBytes = chunks.toLong() * limits.size
        chunks *= 2
    } while (clock.nanos() < end)
    return Pretest(chunks, ChunkSizer.choose(lastBytes, lastNanos, rttNanos, limits))
}

suspend fun RmbtSocket.pretestUpload(chunk: ByteArray, durationNanos: Long, clock: Clock) {
    val end = clock.nanos() + durationNanos
    var chunks = 1
    do {
        putNoResult(chunks, chunk)
        chunks *= 2
    } while (clock.nanos() < end)
}
