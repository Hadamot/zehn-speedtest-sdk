package tj.zehn.rmbt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ExperimentalTime

/**
 * Runs RMBT speed tests. One test at a time: [cancel] stops the running one. [close] releases the HTTP client and
 * the sockets' selector when the client is no longer needed.
 */
@OptIn(ExperimentalAtomicApi::class)
class RmbtClient internal constructor(
    private val config: RmbtConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val connector: Connector = RoutingConnector(socketConnector(), webSocketConnector(httpClient)),
    private val clock: Clock = MonotonicClock(),
) : AutoCloseable {
    /**
     * The running test. Set by the test's coroutine (a background thread) and read by [cancel] from any thread,
     * e.g. Swift's or the UI's, hence atomic; cleared when the test ends.
     */
    private val active = AtomicReference<Job?>(null)

    /** Made on first use and closed with this client: `HttpClient.config` creates a client of its own. */
    private val lazyControl = lazy { ControlClient(config.controlUrl, httpClient, config.apiKey, config.clientInfo) }
    private val control by lazyControl

    constructor() : this(RmbtConfig())

    constructor(config: RmbtConfig) : this(config, defaultHttpClient())

    fun start(): Flow<RmbtEvent> = channelFlow {
        val job = currentCoroutineContext()[Job]
        active.store(job)
        try {
            send(RmbtEvent.Finished(execute { trySend(it) }))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            send(RmbtEvent.Failed(error))
        } finally {
            active.compareAndSet(job, null)
        }
    }
        // The test never runs on the collector's thread (e.g. Android's main thread, where network
        // calls are forbidden); events still arrive in the collector's context.
        .flowOn(Dispatchers.Default)
        .buffer(Channel.UNLIMITED)

    // @Throws: without it a failed test (any exception but CancellationException) terminates an iOS app
    // instead of reaching Swift as an Error.
    @Throws(Throwable::class)
    suspend fun run(): RmbtResult = run {}

    @Throws(Throwable::class)
    suspend fun run(onEvent: (RmbtEvent) -> Unit): RmbtResult = when (val last = start().onEach(onEvent).last()) {
        is RmbtEvent.Finished -> last.result
        is RmbtEvent.Failed -> throw last.error
        else -> error("measurement ended without result")
    }

    fun cancel() {
        active.load()?.cancel()
    }

    /** Stops a running test and closes the connector and the HTTP client (also ones passed in). */
    override fun close() {
        cancel()
        (connector as? AutoCloseable)?.close()
        if (lazyControl.isInitialized()) control.close()
        httpClient.close()
    }

    private suspend fun execute(onEvent: (RmbtEvent) -> Unit): RmbtResult {
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

/**
 * The client for the Control Server and the WebSocket transport. Its WebSocket frame queues are bounded: ktor's
 * default is unlimited, so the upload, which writes as fast as it can for the whole test, queued gigabytes of
 * frames faster than the network sent them (iOS killed the app at its memory limit, ~17 GB on a Mac). With a
 * bound the writer waits for the network, which is what the upload measures.
 */
internal fun defaultHttpClient(): HttpClient = HttpClient {
    install(WebSockets) {
        channels {
            outgoing = bounded(WEBSOCKET_QUEUE_FRAMES)
            incoming = bounded(WEBSOCKET_QUEUE_FRAMES)
        }
    }
}

/** 32 KiB frames ([TestPlan]'s WebSocket chunk cap): at most 1 MiB queued per direction and connection. */
internal const val WEBSOCKET_QUEUE_FRAMES = 32
