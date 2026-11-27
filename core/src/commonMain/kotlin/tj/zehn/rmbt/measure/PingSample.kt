package tj.zehn.rmbt.measure

internal data class PingSample(val clientNanos: Long, val serverNanos: Long, val startNanos: Long)

internal fun List<PingSample>.medianServerNanos(): Long {
    require(isNotEmpty()) { "no pings" }
    val sorted = map { it.serverNanos }.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
}
