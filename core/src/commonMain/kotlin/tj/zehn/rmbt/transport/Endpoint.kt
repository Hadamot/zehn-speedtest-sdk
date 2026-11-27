package tj.zehn.rmbt.transport

import tj.zehn.rmbt.Transport

internal data class Endpoint(val host: String, val port: Int, val transport: Transport)

internal fun interface Connector {
    suspend fun connect(endpoint: Endpoint): Connection
}
