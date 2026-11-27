package tj.zehn.rmbt.measure

import kotlin.time.TimeSource

internal fun interface Clock {
    fun nanos(): Long
}

internal class MonotonicClock : Clock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun nanos(): Long = origin.elapsedNow().inWholeNanoseconds
}
