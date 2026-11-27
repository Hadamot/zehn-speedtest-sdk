package tj.zehn.rmbt.transport

import io.ktor.client.HttpClient

internal actual fun webSocketConnector(client: HttpClient): Connector = WebSocketConnector(client)
