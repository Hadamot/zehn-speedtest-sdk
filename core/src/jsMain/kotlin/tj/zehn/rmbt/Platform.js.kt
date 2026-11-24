package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo

actual object Platform {
    actual val defaultTransport: Transport = Transport.WEBSOCKET
    actual val defaultNetworkType: Int = 98
    actual val clientInfo: ClientInfo = ClientInfo(
        platform = "Browser",
        type = "DESKTOP",
        language = js("(globalThis.navigator && navigator.language || 'en').slice(0, 2)") as String,
        timezone = js("Intl.DateTimeFormat().resolvedOptions().timeZone") as String,
    )
}
