package tj.zehn.rmbt.measure

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import tj.zehn.rmbt.protocol.ClientCommand
import tj.zehn.rmbt.protocol.RmbtSocket
import tj.zehn.rmbt.protocol.ServerMessage
import tj.zehn.rmbt.protocol.UnexpectedMessageException

private const val UPLOAD_GRACE_MS = 3_000L
private const val NANOS_PER_SECOND = 1_000_000_000L

internal suspend fun RmbtSocket.upload(seconds: Int, chunk: ByteArray, clock: Clock, onBytes: (Long) -> Unit): Transfer =
    coroutineScope {
        expect<ServerMessage.Accept>()
        send(ClientCommand.Put(chunk.size))
        expect<ServerMessage.Ok>()
        val samples = ArrayList<Sample>()
        val reports = async { collectReports(samples, onBytes) }
        val end = clock.nanos() + seconds * NANOS_PER_SECOND
        while (clock.nanos() < end) {
            sendChunk(chunk, last = false)
            // A write suspends only when the transport is full: let the report reader run (single-threaded
            // dispatchers, JS) and stop at once when it failed or the test was cancelled.
            yield()
        }
        sendChunk(chunk, last = true)
        val completed = withTimeoutOrNull(UPLOAD_GRACE_MS) { reports.await() } != null
        // Joined, so the reader no longer appends to `samples` while they are copied below.
        if (!completed) reports.cancelAndJoin()
        Transfer(samples.toList(), completed)
    }

private suspend fun RmbtSocket.collectReports(samples: MutableList<Sample>, onBytes: (Long) -> Unit) {
    while (true) {
        when (val message = readMessage()) {
            is ServerMessage.TimeBytes -> {
                samples += Sample(message.nanos, message.bytes)
                onBytes(message.bytes)
            }
            is ServerMessage.Time -> return
            else -> throw UnexpectedMessageException("TIME", message)
        }
    }
}
