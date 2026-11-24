package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo
import java.util.Locale
import java.util.TimeZone

actual object Platform {
    actual val defaultTransport: Transport = Transport.TCP
    actual val defaultNetworkType: Int = 99
    actual val clientInfo: ClientInfo = ClientInfo(
        platform = "Android",
        type = "MOBILE",
        language = Locale.getDefault().language,
        timezone = TimeZone.getDefault().id,
    )
}
