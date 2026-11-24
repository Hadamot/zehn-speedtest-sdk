package tj.zehn.rmbt.measure

import kotlin.time.TimeSource

fun interface Clock {
    fun nanos(): Long
}

class MonotonicClock : Clock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun nanos(): Long = origin.elapsedNow().inWholeNanoseconds
}
