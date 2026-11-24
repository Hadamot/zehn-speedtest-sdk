package tj.zehn.rmbt

data class RmbtConfig(
    val controlUrl: String,
    val apiKey: String? = null,
    val transport: Transport = Transport.TCP,
    val threads: Int? = null,
    val durationSec: Int? = null,
)
