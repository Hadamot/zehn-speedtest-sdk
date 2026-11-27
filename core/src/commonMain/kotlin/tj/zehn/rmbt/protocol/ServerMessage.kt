package tj.zehn.rmbt.protocol

internal sealed interface ServerMessage {
    data class Version(val value: String) : ServerMessage
    data class Accept(val commands: Set<String>) : ServerMessage
    data class ChunkSize(val size: Int, val min: Int, val max: Int) : ServerMessage
    data class Time(val nanos: Long) : ServerMessage
    data class TimeBytes(val nanos: Long, val bytes: Long) : ServerMessage
    data object Ok : ServerMessage
    data object Err : ServerMessage
    data object Bye : ServerMessage
    data object Pong : ServerMessage

    companion object {
        fun parse(line: String): ServerMessage {
            val parts = line.trim().split(' ')
            fun int(index: Int) = parts[index].toIntOrNull() ?: throw MalformedMessageException(line)
            fun long(index: Int) = parts[index].toLongOrNull() ?: throw MalformedMessageException(line)
            return when {
                parts[0].startsWith("RMBTv") -> Version(parts[0])
                parts[0] == "ACCEPT" -> Accept(parts.drop(1).toSet())
                parts[0] == "CHUNKSIZE" && parts.size == 2 -> int(1).let { ChunkSize(it, it, it) }
                parts[0] == "CHUNKSIZE" && parts.size == 4 -> ChunkSize(int(1), int(2), int(3))
                parts[0] == "TIME" && parts.size == 4 && parts[2] == "BYTES" -> TimeBytes(long(1), long(3))
                parts[0] == "TIME" && parts.size == 2 -> Time(long(1))
                parts.size == 1 -> keyword(parts[0])
                else -> throw MalformedMessageException(line)
            }
        }

        private fun keyword(word: String): ServerMessage = when (word) {
            "OK" -> Ok
            "ERR" -> Err
            "BYE" -> Bye
            "PONG" -> Pong
            else -> throw MalformedMessageException(word)
        }
    }
}
