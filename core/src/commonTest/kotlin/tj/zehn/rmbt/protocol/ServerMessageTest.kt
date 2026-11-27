package tj.zehn.rmbt.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerMessageTest {
    @Test
    fun parsesVersionGreeting() {
        assertEquals(ServerMessage.Version("RMBTv1.3.4"), ServerMessage.parse("RMBTv1.3.4\n"))
    }

    @Test
    fun parsesAcceptedCommands() {
        val message = ServerMessage.parse("ACCEPT GETCHUNKS GETTIME PUT PUTNORESULT PING QUIT")

        assertEquals(ServerMessage.Accept(setOf("GETCHUNKS", "GETTIME", "PUT", "PUTNORESULT", "PING", "QUIT")), message)
    }

    @Test
    fun parsesChunkSizeLimits() {
        assertEquals(ServerMessage.ChunkSize(4096, 4096, 4194304), ServerMessage.parse("CHUNKSIZE 4096 4096 4194304"))
    }

    @Test
    fun parsesElapsedTime() {
        assertEquals(ServerMessage.Time(7_000_123_456), ServerMessage.parse("TIME 7000123456"))
    }

    @Test
    fun parsesElapsedTimeWithBytes() {
        assertEquals(ServerMessage.TimeBytes(1_500_000, 65536), ServerMessage.parse("TIME 1500000 BYTES 65536"))
    }

    @Test
    fun parsesBareKeywords() {
        val parsed = listOf("OK", "ERR", "BYE", "PONG").map(ServerMessage::parse)

        assertEquals(listOf(ServerMessage.Ok, ServerMessage.Err, ServerMessage.Bye, ServerMessage.Pong), parsed)
    }

    @Test
    fun rejectsUnknownLine() {
        assertFailsWith<MalformedMessageException> { ServerMessage.parse("HELLO") }
    }
}

class LegacyChunkSizeTest {
    @Test
    fun parsesSingleValueChunkSizeOfOldServers() {
        assertEquals(ServerMessage.ChunkSize(4096, 4096, 4096), ServerMessage.parse("CHUNKSIZE 4096"))
    }

    @Test
    fun aNumberThatIsNotOneIsAProtocolError() {
        kotlin.test.assertFailsWith<MalformedMessageException> { ServerMessage.parse("CHUNKSIZE 4096 x 8192") }
        kotlin.test.assertFailsWith<MalformedMessageException> { ServerMessage.parse("TIME 12a") }
        kotlin.test.assertFailsWith<MalformedMessageException> { ServerMessage.parse("HELLO") }
    }
}
