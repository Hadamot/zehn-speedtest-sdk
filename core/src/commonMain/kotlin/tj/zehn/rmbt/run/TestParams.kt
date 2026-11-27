package tj.zehn.rmbt.run

import tj.zehn.rmbt.measure.ChunkSizer
import tj.zehn.rmbt.transport.Endpoint

internal data class TestParams(
    val endpoint: Endpoint,
    val token: String,
    val threads: Int,
    val durationSec: Int,
    val pings: Int,
    val httpUpgrade: Boolean,
    val pretestNanos: Long = 2_000_000_000,
    val maxChunkSize: Int = ChunkSizer.MAX_CHUNK_SIZE,
)
