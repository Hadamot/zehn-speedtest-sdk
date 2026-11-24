package tj.zehn.rmbt.transport

import io.ktor.client.HttpClient

expect fun webSocketConnector(client: HttpClient): Connector
