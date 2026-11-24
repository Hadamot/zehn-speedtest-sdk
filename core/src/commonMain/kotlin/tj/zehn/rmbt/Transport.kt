package tj.zehn.rmbt

enum class Transport {
    TCP,
    TLS,
    WEBSOCKET,
    SECURE_WEBSOCKET,
    ;

    val isWebSocket: Boolean get() = this == WEBSOCKET || this == SECURE_WEBSOCKET
}
