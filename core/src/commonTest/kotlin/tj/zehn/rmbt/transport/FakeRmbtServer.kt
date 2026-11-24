package tj.zehn.rmbt.transport

import kotlinx.coroutines.channels.Channel

class FakeRmbtServer(private val httpUpgrade: Boolean = false, private val downloadChunks: Int = 3) : Connection {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var current = ByteArray(0)
    private var position = 0
    private val line = StringBuilder()
    private var upload: UploadState? = null
    private var afterOk: String? = null
    val commands = mutableListOf<String>()
    var closed = false

    init {
        if (!httpUpgrade) reply(GREETING)
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position == current.size) {
            current = incoming.receiveCatching().getOrNull() ?: return -1
            position = 0
        }
        val count = minOf(length, current.size - position)
        current.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
        val state = upload
        if (state != null) return consumeUpload(state, buffer, offset, length)
        for (i in offset until offset + length) {
            val char = buffer[i].toInt().toChar()
            if (char == '\n') handle(line.toString().trimEnd('\r')).also { line.clear() } else line.append(char)
        }
    }

    override suspend fun flush() = Unit

    override suspend fun close() {
        closed = true
        incoming.close()
    }

    private fun handle(command: String) {
        commands += command
        val words = command.split(' ')
        when (words[0]) {
            "" -> if (httpUpgrade) reply("HTTP/1.1 101 Switching Protocols\r\nUpgrade: RMBT\r\n\r\n\u0000$GREETING")
            "GET", "Connection:", "Upgrade:", "RMBT-Version:" -> Unit
            "TOKEN" -> reply("OK\nCHUNKSIZE 4096 4096 4194304\n$ACCEPT")
            "GETCHUNKS" -> sendChunks(words[1].toInt(), words.getOrNull(2)?.toInt() ?: 4096, "TIME 1000000\n$ACCEPT")
            "GETTIME" -> sendChunks(downloadChunks, words[2].toInt(), "TIME 7000000000\n$ACCEPT")
            "PING" -> reply("PONG\n").also { afterOk = "TIME 2000000\n$ACCEPT" }
            "OK" -> afterOk?.let(::reply).also { afterOk = null }
            "PUTNORESULT" -> reply("OK\n").also { upload = UploadState(words[1].toInt(), reportBytes = false) }
            "PUT" -> reply("OK\n").also { upload = UploadState(words[1].toInt(), reportBytes = true) }
            "QUIT" -> reply("BYE\n")
        }
    }

    private fun consumeUpload(state: UploadState, buffer: ByteArray, offset: Int, length: Int) {
        state.bytes += length
        val nanos = state.bytes * 1000
        if (state.reportBytes) reply("TIME $nanos BYTES ${state.bytes}\n")
        if (state.bytes % state.chunkSize == 0L && buffer[offset + length - 1] == TERMINATOR) {
            upload = null
            reply("TIME $nanos\n$ACCEPT")
        }
    }

    private fun sendChunks(count: Int, size: Int, after: String) {
        repeat(count) { index -> reply(ByteArray(size).also { it[size - 1] = if (index == count - 1) TERMINATOR else 0 }) }
        afterOk = after
    }

    private fun reply(text: String) = reply(text.encodeToByteArray())

    private fun reply(bytes: ByteArray) {
        incoming.trySend(bytes)
    }

    private class UploadState(val chunkSize: Int, val reportBytes: Boolean, var bytes: Long = 0)

    private companion object {
        const val GREETING = "RMBTv1.3.5\nACCEPT TOKEN QUIT\n"
        const val ACCEPT = "ACCEPT GETCHUNKS GETTIME PUT PUTNORESULT PING QUIT\n"
        const val TERMINATOR: Byte = -1
    }
}
