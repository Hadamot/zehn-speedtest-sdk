package tj.zehn.rmbt.protocol

import kotlinx.coroutines.test.runTest
import tj.zehn.rmbt.transport.FakeRmbtServer
import kotlin.test.Test
import kotlin.test.assertEquals

class HandshakeTest {
    @Test
    fun sendsTokenAndReturnsChunkLimits() = runTest {
        val server = FakeRmbtServer()

        val limits = RmbtSocket(server).handshake("uuid_1_mac", httpUpgrade = false)

        assertEquals(ServerMessage.ChunkSize(4096, 4096, 4194304), limits)
        assertEquals(listOf("TOKEN uuid_1_mac"), server.commands)
    }

    @Test
    fun upgradesHttpConnectionBeforeGreeting() = runTest {
        val server = FakeRmbtServer(httpUpgrade = true)

        val limits = RmbtSocket(server).handshake("uuid_1_mac", httpUpgrade = true)

        assertEquals(4096, limits.size)
        assertEquals("GET /rmbt HTTP/1.1", server.commands.first())
    }
}
