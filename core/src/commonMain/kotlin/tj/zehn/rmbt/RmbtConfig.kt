package tj.zehn.rmbt

import tj.zehn.rmbt.control.ClientInfo

data class RmbtConfig(
    val controlUrl: String = DEFAULT_CONTROL_URL,
    val apiKey: String? = null,
    val clientUuid: String? = null,
    val transport: Transport = Platform.defaultTransport,
    val threads: Int? = null,
    val durationSec: Int? = null,
    val tag: String? = null,
    val networkType: Int = Platform.defaultNetworkType,
    val clientInfo: ClientInfo = Platform.clientInfo,
    val measurementHost: String? = null,
) {
    constructor() : this(DEFAULT_CONTROL_URL, apiKey = null)

    constructor(controlUrl: String, apiKey: String?) : this(controlUrl, apiKey, clientUuid = null)

    /** Without the API key: configurations end up in logs. */
    override fun toString(): String =
        "RmbtConfig(controlUrl=$controlUrl, apiKey=${if (apiKey == null) "null" else "***"}, clientUuid=$clientUuid, " +
            "transport=$transport, threads=$threads, durationSec=$durationSec, tag=$tag, networkType=$networkType, " +
            "clientInfo=$clientInfo, measurementHost=$measurementHost)"

    companion object {
        const val DEFAULT_CONTROL_URL = "https://speedtest.tojiktelecom.tj/RMBTControlServer"
    }
}
