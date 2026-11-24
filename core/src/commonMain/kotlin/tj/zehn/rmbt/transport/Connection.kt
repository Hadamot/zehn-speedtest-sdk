package tj.zehn.rmbt.transport

interface Connection {
    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun flush()
    suspend fun close()
}
