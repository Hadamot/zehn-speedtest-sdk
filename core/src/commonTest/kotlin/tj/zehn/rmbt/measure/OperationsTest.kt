package tj.zehn.rmbt.measure

import kotlinx.coroutines.test.runTest
import tj.zehn.rmbt.protocol.RmbtSocket
import tj.zehn.rmbt.protocol.UnexpectedMessageException
import tj.zehn.rmbt.protocol.handshake
import tj.zehn.rmbt.transport.FakeRmbtServer
import tj.zehn.rmbt.transport.ScriptedConnection
import tj.zehn.rmbt.transport.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OperationsTest {
    private suspend fun connected(server: FakeRmbtServer = FakeRmbtServer()) =
        RmbtSocket(server).also { it.handshake("t", httpUpgrade = false) }

    @Test
    fun getChunksReturnsServerTime() = runTest {
        assertEquals(1_000_000, connected().getChunks(chunks = 2, chunkSize = 4096))
    }

    @Test
    fun pingMeasuresClientAndServerRoundTrip() = runTest {
        val ping = connected().ping(StepClock(10))

        assertEquals(10, ping.clientNanos)
        assertEquals(2_000_000, ping.serverNanos)
    }

    @Test
    fun downloadRecordsCumulativeSamples() = runTest {
        val result = connected(FakeRmbtServer(downloadChunks = 3)).download(7, 4096, StepClock(), start = 0) {}

        assertTrue(result.completed)
        assertEquals(12_288, result.samples.last().bytes)
    }

    @Test
    fun uploadCollectsServerReportsUntilFinalTime() = runTest {
        val result = connected().upload(seconds = 1, chunk = ByteArray(4096), clock = StepClock()) {}

        assertTrue(result.completed)
        assertEquals(result.samples.size * 4096L, result.samples.last().bytes)
    }

    @Test
    fun uploadStopsAtOnceWhenTheServersReportsFail() = runTest {
        // The writes never suspend and the clock never moves: without yielding, the upload would spin forever
        // and never let the reader see the bad report.
        val connection = ScriptedConnection(text("ACCEPT PUT\n"), text("OK\n"), text("ERR\n"))

        assertFailsWith<UnexpectedMessageException> {
            RmbtSocket(connection).upload(seconds = 1, chunk = ByteArray(4096), clock = { 0L }) {}
        }
    }

    @Test
    fun pretestDownloadDoublesChunksUntilDurationEnds() = runTest {
        val socket = connected()
        val limits = tj.zehn.rmbt.protocol.ServerMessage.ChunkSize(4096, 4096, 4194304)

        val pretest = socket.pretestDownload(limits, durationNanos = 200_000_000, clock = StepClock())

        assertEquals(16, pretest.chunks)
    }
}
