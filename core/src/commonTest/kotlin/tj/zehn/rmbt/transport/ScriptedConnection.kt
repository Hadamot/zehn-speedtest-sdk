package tj.zehn.rmbt.transport

class ScriptedConnection(
    vararg incoming: ByteArray,
    private val maxRead: Int = Int.MAX_VALUE,
) : Connection {
    private val pending = ArrayDeque(incoming.toList())
    private var position = 0
    val written = StringBuilder()
    var closed = false

    fun push(bytes: ByteArray) = pending.addLast(bytes)

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val current = pending.firstOrNull() ?: return -1
        val count = minOf(length, maxRead, current.size - position)
        current.copyInto(buffer, offset, position, position + count)
        position += count
        if (position == current.size) {
            pending.removeFirst()
            position = 0
        }
        return count
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
        written.append(buffer.decodeToString(offset, offset + length))
    }

    override suspend fun flush() = Unit

    override suspend fun close() {
        closed = true
    }
}

fun text(value: String): ByteArray = value.encodeToByteArray()

fun chunk(size: Int, last: Boolean): ByteArray =
    ByteArray(size) { 0x55 }.also { it[size - 1] = if (last) -1 else 0 }
