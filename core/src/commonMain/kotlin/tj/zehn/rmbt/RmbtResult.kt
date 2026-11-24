package tj.zehn.rmbt

data class RmbtResult(
    val testUuid: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val serverName: String,
)
