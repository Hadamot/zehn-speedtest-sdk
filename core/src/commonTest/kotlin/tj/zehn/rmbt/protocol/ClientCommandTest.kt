package tj.zehn.rmbt.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientCommandTest {
    @Test
    fun formatsTokenLine() {
        assertEquals("TOKEN abc_123_xyz\n", ClientCommand.Token("abc_123_xyz").line)
    }

    @Test
    fun formatsGetChunksWithOptionalSize() {
        assertEquals("GETCHUNKS 4\n", ClientCommand.GetChunks(4).line)
        assertEquals("GETCHUNKS 4 8192\n", ClientCommand.GetChunks(4, 8192).line)
    }

    @Test
    fun formatsGetTimeWithSize() {
        assertEquals("GETTIME 7 65536\n", ClientCommand.GetTime(7, 65536).line)
    }

    @Test
    fun formatsPutVariants() {
        assertEquals("PUT 65536\n", ClientCommand.Put(65536).line)
        assertEquals("PUTNORESULT\n", ClientCommand.PutNoResult().line)
    }

    @Test
    fun formatsBareCommands() {
        val lines = listOf(ClientCommand.Ping, ClientCommand.Ok, ClientCommand.Quit).map { it.line }

        assertEquals(listOf("PING\n", "OK\n", "QUIT\n"), lines)
    }
}
