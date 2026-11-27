package tj.zehn.rmbt.transport

import io.ktor.client.HttpClient

internal expect fun webSocketConnector(client: HttpClient): Connector
