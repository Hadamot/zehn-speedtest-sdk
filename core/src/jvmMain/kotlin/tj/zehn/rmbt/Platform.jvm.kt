package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo
import java.util.Locale
import java.util.TimeZone

actual object Platform {
    actual val defaultTransport: Transport = Transport.TCP
    actual val defaultNetworkType: Int = 97
    actual val clientInfo: ClientInfo = ClientInfo(
        platform = "JVM",
        type = "DESKTOP",
        language = Locale.getDefault().language,
        timezone = TimeZone.getDefault().id,
    )
}
