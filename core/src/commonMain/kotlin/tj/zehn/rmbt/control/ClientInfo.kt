package tj.zehn.rmbt.control

data class ClientInfo(
    val platform: String = "SDK",
    val type: String = "DESKTOP",
    val language: String = "en",
    val timezone: String = "UTC",
)
