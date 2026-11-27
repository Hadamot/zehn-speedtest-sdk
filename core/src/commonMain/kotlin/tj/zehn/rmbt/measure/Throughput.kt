package tj.zehn.rmbt.measure

internal object Throughput {
    private val ORIGIN = Sample(0, 0)

    fun bitsPerSecond(threads: List<List<Sample>>): Double {
        require(threads.isNotEmpty() && threads.none { it.isEmpty() }) { "every thread needs samples" }
        return measure(threads).bitsPerSecond
    }

    fun measure(threads: List<List<Sample>>): Rate {
        val active = threads.filter { it.isNotEmpty() }
        if (active.isEmpty()) return Rate(0.0, 0)
        val earliestFinish = active.minOf { it.last().nanos }
        return Rate(active.sumOf { bytesAt(it, earliestFinish) }, earliestFinish)
    }

    private fun bytesAt(samples: List<Sample>, nanos: Long): Double {
        val index = samples.indexOfFirst { it.nanos >= nanos }
        val after = samples[index]
        val before = if (index == 0) ORIGIN else samples[index - 1]
        if (after.nanos == before.nanos) return after.bytes.toDouble()
        val share = (nanos - before.nanos).toDouble() / (after.nanos - before.nanos)
        return before.bytes + share * (after.bytes - before.bytes)
    }
}
