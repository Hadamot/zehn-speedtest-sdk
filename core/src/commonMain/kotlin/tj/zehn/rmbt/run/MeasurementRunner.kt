package tj.zehn.rmbt.run

import tj.zehn.rmbt.RmbtEvent
import tj.zehn.rmbt.TestPhase
import tj.zehn.rmbt.measure.Clock
import tj.zehn.rmbt.measure.LiveMeter
import tj.zehn.rmbt.measure.MonotonicClock
import tj.zehn.rmbt.measure.Throughput
import tj.zehn.rmbt.measure.download
import tj.zehn.rmbt.measure.ping
import tj.zehn.rmbt.measure.pretestDownload
import tj.zehn.rmbt.measure.pretestUpload
import tj.zehn.rmbt.measure.upload
import tj.zehn.rmbt.transport.Connector
import kotlin.random.Random

internal class MeasurementRunner(
    private val connector: Connector,
    private val clock: Clock = MonotonicClock(),
    private val onEvent: (RmbtEvent) -> Unit = {},
) {
    suspend fun run(params: TestParams): Measurement {
        val opened = mutableListOf<Session>()
        try {
            phase(TestPhase.INIT)
            repeat(params.threads) { opened += connector.open(params) }
            phase(TestPhase.DOWN_PRETEST)
            val pretests = opened.parallel { _, s -> s.socket.pretestDownload(s.limits, params.pretestNanos, clock, params.maxChunkSize) }
            var sessions = if (pretests.first().chunks <= SLOW_PRETEST_CHUNKS) opened.take(1) else opened.toList()
            opened.drop(sessions.size).forEach { it.close() }
            val chunks = sessions.indices.map { Random.nextBytes(pretests[it].chunkSize) }

            phase(TestPhase.PING)
            val pings = List(params.pings) { sessions.first().socket.ping(clock) }

            phase(TestPhase.DOWNLOAD)
            val durationNanos = params.durationSec * NANOS_PER_SECOND
            val downMeter = LiveMeter(sessions.size, TestPhase.DOWNLOAD, durationNanos, clock, onEvent)
            val start = clock.nanos()
            val downloads = sessions.parallel { i, s ->
                s.socket.download(params.durationSec, chunks[i].size, clock, start) { downMeter.update(i, it) }
            }
            sessions = sessions.mapIndexed { i, s -> if (downloads[i].completed) s else reopen(s, params, opened) }

            phase(TestPhase.UP_PRETEST)
            sessions.parallel { i, s -> s.socket.pretestUpload(chunks[i], params.pretestNanos, clock) }

            phase(TestPhase.UPLOAD)
            val upMeter = LiveMeter(sessions.size, TestPhase.UPLOAD, durationNanos, clock, onEvent)
            val uploads = sessions.parallel { i, s ->
                s.socket.upload(params.durationSec, chunks[i], clock) { upMeter.update(i, it) }
            }

            val downloadSamples = downloads.map { it.samples }
            val uploadSamples = uploads.map { it.samples }
            return Measurement(
                threads = sessions.size,
                download = Throughput.measure(downloadSamples),
                upload = Throughput.measure(uploadSamples),
                downloadSamples = downloadSamples,
                uploadSamples = uploadSamples,
                pings = pings,
            )
        } finally {
            opened.forEach { it.close() }
        }
    }

    private suspend fun reopen(session: Session, params: TestParams, opened: MutableList<Session>): Session {
        session.close()
        return connector.open(params).also { opened += it }
    }

    private fun phase(phase: TestPhase) = onEvent(RmbtEvent.Phase(phase))

    private companion object {
        const val SLOW_PRETEST_CHUNKS = 4
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
