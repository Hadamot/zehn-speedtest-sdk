package tj.zehn.rmbt.protocol

private const val UPGRADE_REQUEST =
    "GET /rmbt HTTP/1.1\r\nConnection: Upgrade\r\nUpgrade: RMBT\r\nRMBT-Version: 1.2.0\r\n\r\n"

suspend fun RmbtSocket.handshake(token: String, httpUpgrade: Boolean): ServerMessage.ChunkSize {
    if (httpUpgrade) upgrade()
    expect<ServerMessage.Version>()
    expect<ServerMessage.Accept>()
    send(ClientCommand.Token(token))
    expect<ServerMessage.Ok>()
    return expect<ServerMessage.ChunkSize>()
}

private suspend fun RmbtSocket.upgrade() {
    sendRaw(UPGRADE_REQUEST)
    val status = readLine()
    if (!status.contains(" 101")) throw UnexpectedMessageException("HTTP 101", status)
    while (readLine().isNotEmpty()) Unit
}
