package tj.zehn.rmbt.measure

class StepClock(private val stepNanos: Long = 50_000_000) : Clock {
    private var now = 0L

    override fun nanos(): Long {
        now += stepNanos
        return now
    }
}
