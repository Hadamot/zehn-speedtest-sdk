package tj.zehn.rmbt.measure

import tj.zehn.rmbt.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkSizerTest {
    private val limits = ServerMessage.ChunkSize(size = 4096, min = 4096, max = 4_194_304)

    @Test
    fun targetsFiftyChunksPerSecondRoundedToKibibyte() {
        val size = ChunkSizer.choose(bytes = 5_120_000, nanos = 1_000_000_000, rttNanos = 0, limits = limits)

        assertEquals(102_400, size)
    }

    @Test
    fun subtractsRoundTripFromTransferTime() {
        val size = ChunkSizer.choose(bytes = 2_560_000, nanos = 600_000_000, rttNanos = 100_000_000, limits = limits)

        assertEquals(102_400, size)
    }

    @Test
    fun clampsToServerMinimum() {
        val size = ChunkSizer.choose(bytes = 1_000, nanos = 1_000_000_000, rttNanos = 0, limits = limits)

        assertEquals(4096, size)
    }

    @Test
    fun capsAtMemoryLimit() {
        val size = ChunkSizer.choose(bytes = 10_000_000_000, nanos = 1_000_000_000, rttNanos = 0, limits = limits)

        assertEquals(ChunkSizer.MAX_CHUNK_SIZE, size)
    }

    @Test
    fun fallsBackToMinimumWithoutMeasurement() {
        assertEquals(4096, ChunkSizer.choose(bytes = 0, nanos = 0, rttNanos = 0, limits = limits))
    }
}

class ChunkSizerCapTest {
    @Test
    fun respectsCallerCap() {
        val limits = ServerMessage.ChunkSize(4096, 4096, 4_194_304)

        val size = ChunkSizer.choose(10_000_000_000, 1_000_000_000, 0, limits, cap = 32_768)

        assertEquals(32_768, size)
    }
}
