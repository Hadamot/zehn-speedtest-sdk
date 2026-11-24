package tj.zehn.rmbt.protocol

import kotlinx.coroutines.test.runTest
import tj.zehn.rmbt.transport.ScriptedConnection
import tj.zehn.rmbt.transport.chunk
import tj.zehn.rmbt.transport.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RmbtSocketTest {
    @Test
    fun readLineStripsCarriageReturnAndNul() = runTest {
        val socket = RmbtSocket(ScriptedConnection(text("\u0000RMBTv1.3.5\r\nACCEPT TOKEN QUIT\n")))

        assertEquals("RMBTv1.3.5", socket.readLine())
        assertEquals("ACCEPT TOKEN QUIT", socket.readLine())
    }

    @Test
    fun readLineJoinsFragmentedReads() = runTest {
        val socket = RmbtSocket(ScriptedConnection(text("PONG\n"), maxRead = 1))

        assertEquals("PONG", socket.readLine())
    }

    @Test
    fun receiveChunksStopsAtTerminatorAndKeepsFollowingLine() = runTest {
        val connection = ScriptedConnection(chunk(4, last = false) + chunk(4, last = true) + text("TIME 5\n"))
        val socket = RmbtSocket(connection)

        val total = socket.receiveChunks(chunkSize = 4) {}

        assertEquals(8, total)
        assertEquals("TIME 5", socket.readLine())
    }

    @Test
    fun receiveChunksIgnoresTerminatorValueInsideChunk() = runTest {
        val first = byteArrayOf(-1, -1, -1, 0)
        val socket = RmbtSocket(ScriptedConnection(first, chunk(4, last = true), maxRead = 3))

        val total = socket.receiveChunks(chunkSize = 4) {}

        assertEquals(8, total)
    }

    @Test
    fun reportsCumulativeBytesWhileReceiving() = runTest {
        val socket = RmbtSocket(ScriptedConnection(chunk(4, false), chunk(4, true)))
        val progress = mutableListOf<Long>()

        socket.receiveChunks(chunkSize = 4) { progress += it }

        assertEquals(listOf(4L, 8L), progress)
    }

    @Test
    fun sendWritesCommandLine() = runTest {
        val connection = ScriptedConnection()

        RmbtSocket(connection).send(ClientCommand.Ping)

        assertEquals("PING\n", connection.written.toString())
    }

    @Test
    fun failsWhenConnectionEnds() = runTest {
        val socket = RmbtSocket(ScriptedConnection(text("PAR")))

        assertFailsWith<ConnectionClosedException> { socket.readLine() }
    }
}
