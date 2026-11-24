package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo

data class RmbtConfig(
    val controlUrl: String,
    val apiKey: String? = null,
    val clientUuid: String? = null,
    val transport: Transport = Platform.defaultTransport,
    val threads: Int? = null,
    val durationSec: Int? = null,
    val tag: String? = null,
    val networkType: Int = Platform.defaultNetworkType,
    val clientInfo: ClientInfo = Platform.clientInfo,
    val measurementHost: String? = null,
)
