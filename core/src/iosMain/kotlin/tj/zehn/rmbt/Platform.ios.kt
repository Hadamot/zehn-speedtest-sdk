package tj.zehn.rmbt

import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localTimeZone
import tj.zehn.rmbt.control.ClientInfo

actual object Platform {
    actual val defaultTransport: Transport = Transport.TCP
    actual val defaultNetworkType: Int = 99
    actual val clientInfo: ClientInfo = ClientInfo(
        platform = "iOS",
        type = "MOBILE",
        language = NSLocale.currentLocale.languageCode,
        timezone = NSTimeZone.localTimeZone.name,
    )
}
