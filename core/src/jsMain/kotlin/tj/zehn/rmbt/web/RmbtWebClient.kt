@file:OptIn(ExperimentalJsExport::class)

package tj.zehn.rmbt.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.promise
import tj.zehn.rmbt.RmbtClient
import tj.zehn.rmbt.RmbtEvent
import kotlin.js.Promise

@JsExport
class RmbtWebResult(
    val testUuid: String,
    val openTestUuid: String?,
    val clientUuid: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val serverName: String,
)

@JsExport
class RmbtWebClient(options: RmbtWebOptions) {
    private val client = RmbtClient(options.toConfig())
    private val scope = CoroutineScope(SupervisorJob())

    fun start(
        onPhase: (phase: String) -> Unit = {},
        onProgress: (phase: String, fraction: Double, mbps: Double) -> Unit = { _, _, _ -> },
    ): Promise<RmbtWebResult> = scope.promise {
        var result: RmbtWebResult? = null
        client.start().collect { event ->
            when (event) {
                is RmbtEvent.Phase -> onPhase(event.phase.name)
                is RmbtEvent.Progress -> onProgress(event.phase.name, event.fraction, event.mbps)
                is RmbtEvent.Failed -> throw event.error
                is RmbtEvent.Finished -> result = with(event.result) {
                    RmbtWebResult(testUuid, openTestUuid, clientUuid, downloadMbps, uploadMbps, pingMs, serverName)
                }
            }
        }
        result ?: error("measurement ended without result")
    }

    fun cancel() = scope.coroutineContext.cancelChildren()
}
