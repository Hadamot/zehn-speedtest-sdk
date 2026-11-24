package tj.zehn.rmbt.measure

import tj.zehn.rmbt.protocol.ServerMessage

object ChunkSizer {
    const val MAX_CHUNK_SIZE = 256 * 1024
    private const val CHUNKS_PER_SECOND = 50
    private const val KIB = 1024

    fun choose(bytes: Long, nanos: Long, rttNanos: Long, limits: ServerMessage.ChunkSize): Int {
        val low = limits.min
        val high = minOf(limits.max, MAX_CHUNK_SIZE)
        val transferNanos = if (nanos > rttNanos) nanos - rttNanos else nanos
        if (bytes <= 0 || transferNanos <= 0) return low
        val bytesPerSecond = bytes * 1e9 / transferNanos
        val ideal = (bytesPerSecond / CHUNKS_PER_SECOND).toLong()
        val rounded = (ideal + KIB / 2) / KIB * KIB
        return rounded.coerceIn(low.toLong(), high.toLong()).toInt()
    }
}
