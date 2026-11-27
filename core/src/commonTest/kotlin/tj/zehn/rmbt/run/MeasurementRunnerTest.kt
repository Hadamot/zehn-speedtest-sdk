package tj.zehn.rmbt.run

import kotlinx.coroutines.test.runTest
import tj.zehn.rmbt.RmbtEvent
import tj.zehn.rmbt.TestPhase
import tj.zehn.rmbt.Transport
import tj.zehn.rmbt.measure.StepClock
import tj.zehn.rmbt.transport.Endpoint
import tj.zehn.rmbt.transport.FakeRmbtServer
import tj.zehn.rmbt.transport.ScriptedConnection
import tj.zehn.rmbt.transport.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MeasurementRunnerTest {
    private val servers = mutableListOf<FakeRmbtServer>()
    private val events = mutableListOf<RmbtEvent>()
    private val runner = MeasurementRunner(
        connector = { FakeRmbtServer().also { servers += it } },
        clock = StepClock(),
        onEvent = { events += it },
    )

    private fun params(pretestNanos: Long) = TestParams(
        endpoint = Endpoint("localhost", 8081, Transport.TCP),
        token = "token",
        threads = 3,
        durationSec = 1,
        pings = 3,
        httpUpgrade = false,
        pretestNanos = pretestNanos,
    )

    @Test
    fun runsAllPhasesInOrder() = runTest {
        runner.run(params(pretestNanos = 200_000_000))

        val phases = events.filterIsInstance<RmbtEvent.Phase>().map { it.phase }
        assertEquals(TestPhase.entries.dropLast(1), phases)
    }

    @Test
    fun measuresDownloadUploadAndPing() = runTest {
        val measurement = runner.run(params(pretestNanos = 200_000_000))

        assertEquals(3, measurement.threads)
        assertTrue(measurement.download.bitsPerSecond > 0)
        assertTrue(measurement.upload.bitsPerSecond > 0)
        assertEquals(3, measurement.pings.size)
    }

    @Test
    fun fallsBackToOneThreadOnSlowPretest() = runTest {
        val measurement = runner.run(params(pretestNanos = 60_000_000))

        assertEquals(1, measurement.threads)
    }

    @Test
    fun closesEveryConnection() = runTest {
        runner.run(params(pretestNanos = 200_000_000))

        assertTrue(servers.all { it.closed })
    }

    @Test
    fun aFailedHandshakeClosesTheConnection() = runTest {
        // The server rejects the token.
        val connection = ScriptedConnection(text("RMBTv1.2.0\n"), text("ACCEPT TOKEN QUIT\n"), text("ERR\n"))
        val connector = tj.zehn.rmbt.transport.Connector { connection }

        assertFails { connector.open(params(pretestNanos = 1)) }

        assertTrue(connection.closed)
    }
}
