package tj.zehn.rmbt.measure

data class Rate(val bytes: Double, val nanos: Long) {
    val bitsPerSecond: Double get() = if (nanos <= 0) 0.0 else bytes * 8 * 1e9 / nanos
    val mbps: Double get() = bitsPerSecond / 1e6
}
