package tj.zehn.rmbt.measure

object Throughput {
    private val ORIGIN = Sample(0, 0)
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    fun bitsPerSecond(threads: List<List<Sample>>): Double {
        require(threads.isNotEmpty() && threads.none { it.isEmpty() }) { "every thread needs samples" }
        val earliestFinish = threads.minOf { it.last().nanos }
        val bytes = threads.sumOf { bytesAt(it, earliestFinish) }
        return bytes * 8 * NANOS_PER_SECOND / earliestFinish
    }

    private fun bytesAt(samples: List<Sample>, nanos: Long): Double {
        val index = samples.indexOfFirst { it.nanos >= nanos }
        val after = samples[index]
        val before = if (index == 0) ORIGIN else samples[index - 1]
        val share = (nanos - before.nanos).toDouble() / (after.nanos - before.nanos)
        return before.bytes + share * (after.bytes - before.bytes)
    }
}
