package tj.zehn.rmbt.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettingsRequest(
    val uuid: String?,
    val type: String,
    val name: String = "RMBT",
    val language: String,
    @SerialName("plattform") val platform: String,
    @SerialName("terms_and_conditions_accepted") val termsAccepted: Boolean = true,
    @SerialName("terms_and_conditions_accepted_version") val termsVersion: Int = 6,
)

@Serializable
data class SettingsResponse(val settings: List<SettingsItem> = emptyList(), val error: List<String> = emptyList())

@Serializable
data class SettingsItem(val uuid: String)

@Serializable
data class TestRequest(
    val uuid: String,
    val type: String,
    val language: String,
    @SerialName("plattform") val platform: String,
    val time: Long,
    val timezone: String,
    val client: String = "RMBTws",
    val version: String = "0.3",
    @SerialName("num_threads") val threads: Int? = null,
    val tag: String? = null,
)
