package tj.zehn.rmbt.measure

import tj.zehn.rmbt.RmbtEvent
import tj.zehn.rmbt.TestPhase
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicLongArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LiveMeter(
    threads: Int,
    private val phase: TestPhase,
    private val durationNanos: Long,
    private val clock: Clock,
    private val onEvent: (RmbtEvent) -> Unit,
) {
    private val bytes = AtomicLongArray(threads)
    private val start = clock.nanos()
    private val lastEmit = AtomicLong(start)

    fun update(thread: Int, total: Long) {
        bytes.storeAt(thread, total)
        val now = clock.nanos()
        val last = lastEmit.load()
        if (now - last < INTERVAL_NANOS || !lastEmit.compareAndSet(last, now)) return
        val elapsed = now - start
        var sum = 0L
        for (index in 0 until bytes.size) sum += bytes.loadAt(index)
        val fraction = (elapsed.toDouble() / durationNanos).coerceAtMost(1.0)
        onEvent(RmbtEvent.Progress(phase, fraction, sum * 8e3 / elapsed))
    }

    private companion object {
        const val INTERVAL_NANOS = 100_000_000L
    }
}
