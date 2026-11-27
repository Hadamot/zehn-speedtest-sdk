package tj.zehn.rmbt.ui.screen

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tj.zehn.rmbt.RmbtClient
import tj.zehn.rmbt.RmbtConfig
import tj.zehn.rmbt.RmbtEvent
import tj.zehn.rmbt.RmbtResult
import tj.zehn.rmbt.TestPhase
import tj.zehn.rmbt.ui.RmbtUi
import tj.zehn.rmbt.ui.data.FusedLocationSource
import tj.zehn.rmbt.ui.data.HistoryStore
import tj.zehn.rmbt.ui.data.NetworkMonitor
import tj.zehn.rmbt.ui.data.NetworkState
import tj.zehn.rmbt.ui.data.TestRecordSink
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.MeasurementState
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.model.TestRecord
import tj.zehn.rmbt.ui.view.SpeedPoint
import java.util.TimeZone
import kotlin.math.roundToInt

/** What the test screen shows. Speeds in bit/s, ping in ns, as the copied views expect. */
internal data class MeasurementUiState(
    val network: NetworkState = NetworkState.OFFLINE,
    val state: MeasurementState = MeasurementState.IDLE,
    /** Progress of the current step, 0..100. */
    val progress: Int = 0,
    /** Live speed of the running download or upload. */
    val liveSpeedBps: Long = 0,
    val downloadBps: Long = 0,
    val uploadBps: Long = 0,
    val pingNanos: Long = 0,
    /** Live speed points of the running download or upload, for the chart. */
    val chart: List<SpeedPoint> = emptyList(),
)

/** How the test ended; kept until the screen has reacted, so a recreated screen still sees it. */
internal sealed interface MeasurementOutcome {
    data class Finished(val testUuid: String) : MeasurementOutcome
    data class Failed(val error: Throwable) : MeasurementOutcome
}

/**
 * Runs one test as soon as the screen opens; survives rotation. The dependencies come from [Factory]
 * ([RmbtClient], the device history, the current network); tests pass fakes.
 *
 * @param startTest starts the test with the given settings, [RmbtClient.start] by default.
 * @param locate the device location, or null when it is not available; asked once, while the test runs.
 * @param elapsedRealtime monotonic clock for the progress by time, in ms.
 * @param now wall clock for the stored test time, in ms.
 */
internal class MeasurementViewModel internal constructor(
    private val store: TestRecordSink,
    private val startNetwork: NetworkState,
    private val startTest: (RmbtConfig) -> Flow<RmbtEvent>,
    private val locate: suspend () -> GeoPoint? = { null },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasurementUiState(network = startNetwork))
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()

    private val _outcome = MutableStateFlow<MeasurementOutcome?>(null)
    val outcome: StateFlow<MeasurementOutcome?> = _outcome.asStateFlow()

    private var test: Job? = null

    /** Looked up next to the test, so it is usually there when the test ends; null on any failure. */
    private val location: Deferred<GeoPoint?> = viewModelScope.async {
        try {
            locate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    private var ticker: Job? = null

    init {
        test = viewModelScope.launch { run() }
    }

    /** Stops the running test; a test that already finished or failed keeps its outcome. */
    fun cancel() {
        ticker?.cancel()
        location.cancel()
        test?.cancel()
        _uiState.update {
            if (it.state == MeasurementState.FINISH || it.state == MeasurementState.ERROR) it
            else it.copy(state = MeasurementState.ABORTED)
        }
    }

    private suspend fun run() {
        val ui = RmbtUi.config
        val config = RmbtConfig(
            controlUrl = ui.controlUrl,
            apiKey = ui.apiKey,
            clientUuid = store.clientUuid(),
            tag = ui.tag,
            networkType = startNetwork.rmbtNetworkType,
        )
        enter(MeasurementState.INIT)
        startTest(config).collect { event ->
            when (event) {
                is RmbtEvent.Phase -> onPhase(event.phase)
                is RmbtEvent.Progress -> onProgress(event)
                is RmbtEvent.Finished -> onFinished(event.result)
                is RmbtEvent.Failed -> {
                    ticker?.cancel()
                    _uiState.update { it.copy(state = MeasurementState.ERROR) }
                    _outcome.value = MeasurementOutcome.Failed(event.error)
                }
            }
        }
    }

    private fun onPhase(phase: TestPhase) {
        val state = MeasurementState.of(phase)
        when (phase) {
            // The last download value stays as the download result until the test ends.
            TestPhase.UP_PRETEST -> _uiState.update { it.copy(downloadBps = it.liveSpeedBps) }
            TestPhase.SUBMIT -> _uiState.update { it.copy(progress = 100, uploadBps = it.liveSpeedBps) }
            else -> Unit
        }
        if (state != _uiState.value.state) enter(state)
    }

    /**
     * The SDK reports progress only while it transfers data; connecting and the pings get a progress
     * by time so the curve keeps moving.
     */
    private fun enter(state: MeasurementState) {
        ticker?.cancel()
        _uiState.update { it.copy(state = state, progress = 0, liveSpeedBps = 0, chart = emptyList()) }
        val expectedMs = EXPECTED_STEP_MS[state] ?: return
        val start = elapsedRealtime()
        ticker = viewModelScope.launch {
            while (isActive) {
                val elapsed = elapsedRealtime() - start
                val progress = (elapsed * 100 / expectedMs).toInt().coerceAtMost(TIMED_PROGRESS_MAX)
                _uiState.update { if (it.state == state) it.copy(progress = progress) else it }
                delay(TICK_MS)
            }
        }
    }

    private fun onProgress(event: RmbtEvent.Progress) {
        val state = MeasurementState.of(event.phase)
        val progress = (event.fraction * 100).roundToInt().coerceIn(0, 100)
        val speed = (event.mbps * 1_000_000).toLong()
        _uiState.update {
            if (it.state != state) {
                it
            } else {
                it.copy(progress = progress, liveSpeedBps = speed, chart = it.chart + SpeedPoint(progress, speed))
            }
        }
    }

    private suspend fun onFinished(result: RmbtResult) {
        ticker?.cancel()
        // The test takes far longer than a fix; a missing one never holds the result back for long.
        val point = withTimeoutOrNull(LOCATION_WAIT_MS) { location.await() }
        store.saveClientUuid(result.clientUuid)
        store.add(
            TestRecord(
                testUuid = result.testUuid,
                openTestUuid = result.openTestUuid,
                timeMillis = now(),
                timezone = TimeZone.getDefault().id,
                downloadMbps = result.downloadMbps,
                uploadMbps = result.uploadMbps,
                pingMs = result.pingMs,
                serverName = result.serverName,
                network = startNetwork.kind ?: NetworkKind.OTHER,
                networkName = startNetwork.name,
                signalLevel = startNetwork.signalLevel,
                location = point,
            )
        )
        _uiState.update {
            it.copy(
                state = MeasurementState.FINISH,
                progress = 100,
                downloadBps = (result.downloadMbps * 1_000_000).toLong(),
                uploadBps = (result.uploadMbps * 1_000_000).toLong(),
                pingNanos = (result.pingMs * 1_000_000).toLong(),
            )
        }
        _outcome.value = MeasurementOutcome.Finished(result.testUuid)
    }

    companion object {
        /** The real dependencies: [RmbtClient], the device history and the network at the start. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MeasurementViewModel(
                    store = HistoryStore.get(app),
                    startNetwork = NetworkMonitor(app).current(),
                    startTest = { config ->
                        // A client per test, closed with it: it owns sockets' threads and an HTTP client.
                        val client = RmbtClient(config)
                        client.start().onCompletion { client.close() }
                    },
                    locate = FusedLocationSource(app)::current,
                )
            }
        }

        private const val TICK_MS = 100L
        private const val LOCATION_WAIT_MS = 3_000L
        private const val TIMED_PROGRESS_MAX = 95
        private val EXPECTED_STEP_MS = mapOf(
            MeasurementState.INIT to 3_000L,
            MeasurementState.PING to 1_500L,
        )
    }
}
