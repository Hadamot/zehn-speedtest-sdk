@file:OptIn(ExperimentalJsExport::class)

package tj.zehn.rmbt.web

import tj.zehn.rmbt.RmbtConfig

@JsExport
class RmbtWebOptions(
    val controlUrl: String,
    val apiKey: String? = null,
    val clientUuid: String? = null,
    val threads: Int? = null,
    val durationSec: Int? = null,
    val tag: String? = null,
    val measurementHost: String? = null,
)

internal fun RmbtWebOptions.toConfig() = RmbtConfig(
    controlUrl = controlUrl,
    apiKey = apiKey,
    clientUuid = clientUuid,
    threads = threads,
    durationSec = durationSec,
    tag = tag,
    measurementHost = measurementHost,
)
