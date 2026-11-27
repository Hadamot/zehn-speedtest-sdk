package tj.zehn.rmbt

import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localTimeZone
import tj.zehn.rmbt.control.ClientInfo

actual object Platform {
    // ktor has no TLS sockets on Kotlin/Native ("TLS sessions are not supported on Native platform"), so the
    // encrypted test goes over a secure WebSocket (URLSession) instead of TLS over TCP.
    actual val defaultTransport: Transport = Transport.WEBSOCKET
    actual val defaultNetworkType: Int = 99
    actual val clientInfo: ClientInfo = ClientInfo(
        platform = "iOS",
        type = "MOBILE",
        language = NSLocale.currentLocale.languageCode,
        timezone = NSTimeZone.localTimeZone.name,
    )
}
