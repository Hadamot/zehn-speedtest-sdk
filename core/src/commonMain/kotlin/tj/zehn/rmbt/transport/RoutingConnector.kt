package tj.zehn.rmbt.transport

internal class RoutingConnector(private val socket: Connector?, private val webSocket: Connector) : Connector {
    override suspend fun connect(endpoint: Endpoint): Connection =
        if (endpoint.transport.isWebSocket) {
            webSocket.connect(endpoint)
        } else {
            val connector = socket ?: throw UnsupportedOperationException("${endpoint.transport} is not available here")
            connector.connect(endpoint)
        }
}

internal expect fun socketConnector(): Connector?
