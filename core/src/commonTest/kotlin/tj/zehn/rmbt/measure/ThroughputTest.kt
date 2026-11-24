package tj.zehn.rmbt.measure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThroughputTest {
    @Test
    fun singleThreadRateIsBytesOverTime() {
        val threads = listOf(listOf(Sample(nanos = 2_000_000_000, bytes = 1_000_000)))

        val bps = Throughput.bitsPerSecond(threads)

        assertEquals(4_000_000.0, bps)
    }

    @Test
    fun slowerThreadIsInterpolatedAtEarliestFinish() {
        val threads = listOf(
            listOf(Sample(1_000_000_000, 1_000)),
            listOf(Sample(500_000_000, 400), Sample(1_500_000_000, 1_400)),
        )

        val bps = Throughput.bitsPerSecond(threads)

        assertEquals(15_200.0, bps)
    }

    @Test
    fun interpolatesFromOriginWhenFirstSampleIsAfterEarliestFinish() {
        val threads = listOf(
            listOf(Sample(1_000_000_000, 1_000)),
            listOf(Sample(2_000_000_000, 2_000)),
        )

        val bps = Throughput.bitsPerSecond(threads)

        assertEquals(16_000.0, bps)
    }

    @Test
    fun rejectsThreadWithoutSamples() {
        val threads = listOf(listOf(Sample(1_000_000_000, 1_000)), emptyList())

        assertFailsWith<IllegalArgumentException> { Throughput.bitsPerSecond(threads) }
    }
}
