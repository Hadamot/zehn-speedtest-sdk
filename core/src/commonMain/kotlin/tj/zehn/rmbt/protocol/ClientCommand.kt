package tj.zehn.rmbt.protocol

sealed class ClientCommand(vararg words: Any?) {
    val line: String = words.filterNotNull().joinToString(" ") + "\n"

    class Token(token: String) : ClientCommand("TOKEN", token)
    class GetChunks(chunks: Int, chunkSize: Int? = null) : ClientCommand("GETCHUNKS", chunks, chunkSize)
    class GetTime(seconds: Int, chunkSize: Int? = null) : ClientCommand("GETTIME", seconds, chunkSize)
    class Put(chunkSize: Int? = null) : ClientCommand("PUT", chunkSize)
    class PutNoResult(chunkSize: Int? = null) : ClientCommand("PUTNORESULT", chunkSize)
    data object Ping : ClientCommand("PING")
    data object Ok : ClientCommand("OK")
    data object Quit : ClientCommand("QUIT")
}
