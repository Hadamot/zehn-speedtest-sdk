package tj.zehn.rmbt.control

import tj.zehn.rmbt.measure.Sample
import tj.zehn.rmbt.run.Measurement

object ResultMapper {
    private const val DETAIL_INTERVAL_NANOS = 100_000_000L

    fun map(
        measurement: Measurement,
        ticket: TestTicket.Parsed,
        clientUuid: String,
        info: ClientInfo,
        networkType: Int,
        timeMillis: Long,
        tag: String? = null,
    ): ResultRequest {
        val firstPing = measurement.pings.minOfOrNull { it.startNanos } ?: 0
        return ResultRequest(
            platform = info.platform,
            clientUuid = clientUuid,
            language = info.language,
            time = timeMillis,
            testToken = ticket.token,
            portRemote = ticket.port,
            bytesDownload = measurement.download.bytes.toLong(),
            bytesUpload = measurement.upload.bytes.toLong(),
            totalBytesDownload = measurement.downloadSamples.totalBytes(),
            totalBytesUpload = measurement.uploadSamples.totalBytes(),
            encryption = if (ticket.encrypted) "TLS" else null,
            nsecDownload = measurement.download.nanos,
            nsecUpload = measurement.upload.nanos,
            threads = measurement.threads,
            threadsUpload = measurement.threads,
            speedDownloadKbps = (measurement.download.bitsPerSecond / 1000).toLong(),
            speedUploadKbps = (measurement.upload.bitsPerSecond / 1000).toLong(),
            pingShortestNanos = measurement.pings.minOfOrNull { it.clientNanos } ?: 0,
            networkType = networkType,
            pings = measurement.pings.map { PingBody(it.clientNanos, it.serverNanos, it.startNanos - firstPing) },
            speedDetail = details("download", measurement.downloadSamples) + details("upload", measurement.uploadSamples),
            tag = tag,
        )
    }

    private fun List<List<Sample>>.totalBytes() = sumOf { it.lastOrNull()?.bytes ?: 0 }

    private fun details(direction: String, threads: List<List<Sample>>) = threads.flatMapIndexed { thread, samples ->
        coarse(samples).map { SpeedBody(direction, thread, it.nanos, it.bytes) }
    }

    private fun coarse(samples: List<Sample>): List<Sample> {
        val kept = samples.distinctBy { it.nanos / DETAIL_INTERVAL_NANOS }
        val last = samples.lastOrNull() ?: return kept
        return if (kept.last() == last) kept else kept + last
    }
}
