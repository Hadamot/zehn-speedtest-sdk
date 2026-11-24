package tj.zehn.rmbt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import tj.zehn.rmbt.control.ControlClient
import tj.zehn.rmbt.control.ResultMapper
import tj.zehn.rmbt.measure.Clock
import tj.zehn.rmbt.measure.MonotonicClock
import tj.zehn.rmbt.measure.medianServerNanos
import tj.zehn.rmbt.run.MeasurementRunner
import tj.zehn.rmbt.transport.Connector
import tj.zehn.rmbt.transport.RoutingConnector
import tj.zehn.rmbt.transport.webSocketConnector
import tj.zehn.rmbt.transport.socketConnector
import kotlin.time.ExperimentalTime

class RmbtClient(
    private val config: RmbtConfig,
    private val httpClient: HttpClient = HttpClient { install(WebSockets) },
    private val connector: Connector = RoutingConnector(socketConnector(), webSocketConnector(httpClient)),
    private val clock: Clock = MonotonicClock(),
) {
    private var active: Job? = null

    constructor(config: RmbtConfig) : this(config, HttpClient { install(WebSockets) })

    fun start(): Flow<RmbtEvent> = channelFlow {
        active = currentCoroutineContext()[Job]
        try {
            send(RmbtEvent.Finished(execute { trySend(it) }))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            send(RmbtEvent.Failed(error))
        }
    }.buffer(Channel.UNLIMITED)

    suspend fun run(): RmbtResult = run {}

    suspend fun run(onEvent: (RmbtEvent) -> Unit): RmbtResult = when (val last = start().onEach(onEvent).last()) {
        is RmbtEvent.Finished -> last.result
        is RmbtEvent.Failed -> throw last.error
        else -> error("measurement ended without result")
    }

    fun cancel() {
        active?.cancel()
    }

    private suspend fun execute(onEvent: (RmbtEvent) -> Unit): RmbtResult {
        val control = ControlClient(config.controlUrl, httpClient, config.apiKey, config.clientInfo)
        val clientUuid = config.clientUuid ?: control.register(null)
        val ticket = control.requestTest(clientUuid, config.threads, config.tag, nowMillis()).parsed()
        if (ticket.waitSec > 0) delay(ticket.waitSec * 1_000L)
        val measurement = MeasurementRunner(connector, clock, onEvent).run(TestPlan.params(config, ticket))
        onEvent(RmbtEvent.Phase(TestPhase.SUBMIT))
        val result = ResultMapper.map(measurement, ticket, clientUuid, config.clientInfo, config.networkType, nowMillis(), config.tag)
        control.submitResult(result)
        return RmbtResult(
            testUuid = ticket.testUuid,
            openTestUuid = ticket.openTestUuid,
            clientUuid = clientUuid,
            downloadMbps = measurement.download.mbps,
            uploadMbps = measurement.upload.mbps,
            pingMs = measurement.pings.medianServerNanos() / 1e6,
            serverName = ticket.serverName,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
