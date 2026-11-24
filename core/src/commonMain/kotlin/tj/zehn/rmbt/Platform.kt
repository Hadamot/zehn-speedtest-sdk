package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo

expect object Platform {
    val defaultTransport: Transport
    val defaultNetworkType: Int
    val clientInfo: ClientInfo
}
