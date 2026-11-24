package tj.zehn.rmbt.protocol

import tj.zehn.rmbt.transport.Connection

class RmbtSocket(private val connection: Connection, bufferSize: Int = 64 * 1024) {
    private val buffer = ByteArray(bufferSize)
    private var start = 0
    private var end = 0

    suspend fun readLine(): String {
        val line = StringBuilder()
        while (true) {
            if (start == end) fill()
            val char = buffer[start++].toInt().toChar()
            when (char) {
                '\n' -> return line.toString()
                '\r', '\u0000' -> Unit
                else -> line.append(char)
            }
        }
    }

    suspend fun readMessage(): ServerMessage = ServerMessage.parse(readLine())

    suspend inline fun <reified T : ServerMessage> expect(): T {
        val message = readMessage()
        return message as? T ?: throw UnexpectedMessageException(T::class.simpleName.orEmpty(), message)
    }

    suspend fun send(command: ClientCommand) {
        val bytes = command.line.encodeToByteArray()
        connection.write(bytes, 0, bytes.size)
        connection.flush()
    }

    suspend fun sendRaw(text: String) {
        val bytes = text.encodeToByteArray()
        connection.write(bytes, 0, bytes.size)
        connection.flush()
    }

    suspend fun sendChunk(chunk: ByteArray, last: Boolean) {
        chunk[chunk.size - 1] = if (last) TERMINATOR else CONTINUE
        connection.write(chunk, 0, chunk.size)
        if (last) connection.flush()
    }

    suspend fun receiveChunks(chunkSize: Int, onBytes: (Long) -> Unit): Long {
        var total = 0L
        while (true) {
            if (start == end) fill()
            val toBoundary = chunkSize - (total % chunkSize).toInt()
            val take = minOf(toBoundary, end - start)
            start += take
            total += take
            onBytes(total)
            if (take == toBoundary && buffer[start - 1] == TERMINATOR) return total
        }
    }

    suspend fun close() = connection.close()

    private suspend fun fill() {
        val count = connection.read(buffer, 0, buffer.size)
        if (count <= 0) throw ConnectionClosedException()
        start = 0
        end = count
    }

    private companion object {
        const val TERMINATOR: Byte = -1
        const val CONTINUE: Byte = 0
    }
}
