package tj.zehn.rmbt.ui.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tj.zehn.rmbt.RmbtConfig
import tj.zehn.rmbt.RmbtEvent
import tj.zehn.rmbt.RmbtResult
import tj.zehn.rmbt.TestPhase
import tj.zehn.rmbt.ui.RmbtUi
import tj.zehn.rmbt.ui.RmbtUiConfig
import tj.zehn.rmbt.ui.data.NetworkState
import tj.zehn.rmbt.ui.data.TestRecordSink
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.MeasurementState
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.model.TestRecord

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val events = Channel<RmbtEvent>(Channel.UNLIMITED)
    private val store = FakeStore(clientUuid = "client-1")
    private val wifi = NetworkState(connected = true, kind = NetworkKind.WIFI, name = "Home", signalLevel = 3)
    private var startedWith: RmbtConfig? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RmbtUi.configure(RmbtUiConfig(controlUrl = "https://control.example", apiKey = "key", tag = "probe"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        RmbtUi.configure(RmbtUiConfig())
    }

    @Test
    fun `starts the test with the host settings, the stored client and the network`() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        val config = startedWith!!
        assertEquals("https://control.example", config.controlUrl)
        assertEquals("key", config.apiKey)
        assertEquals("probe", config.tag)
        assertEquals("client-1", config.clientUuid)
        assertEquals(99, config.networkType)
        viewModel.cancel()
    }

    @Test
    fun `connecting gets a progress by time that stops short of the end`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        assertEquals(MeasurementState.INIT, viewModel.uiState.value.state)
        assertEquals(wifi, viewModel.uiState.value.network)

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(50, viewModel.uiState.value.progress)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(95, viewModel.uiState.value.progress)
        viewModel.cancel()
    }

    @Test
    fun `each new step starts from zero`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        advanceTimeBy(2_000)

        send(RmbtEvent.Phase(TestPhase.PING))

        assertEquals(MeasurementState.PING, viewModel.uiState.value.state)
        assertEquals(0, viewModel.uiState.value.progress)
        viewModel.cancel()
    }

    @Test
    fun `download progress moves the curve and the chart, a stale step is ignored`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        send(RmbtEvent.Phase(TestPhase.DOWNLOAD))

        send(RmbtEvent.Progress(TestPhase.DOWNLOAD, fraction = 0.25, mbps = 40.0))
        send(RmbtEvent.Progress(TestPhase.DOWNLOAD, fraction = 0.5, mbps = 50.0))
        send(RmbtEvent.Progress(TestPhase.UPLOAD, fraction = 0.9, mbps = 1.0))

        val state = viewModel.uiState.value
        assertEquals(MeasurementState.DOWNLOAD, state.state)
        assertEquals(50, state.progress)
        assertEquals(50_000_000L, state.liveSpeedBps)
        assertEquals(listOf(25 to 40_000_000L, 50 to 50_000_000L), state.chart.map { it.progress to it.value })
        viewModel.cancel()
    }

    @Test
    fun `the last download speed stays as the download result during the upload`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        send(RmbtEvent.Phase(TestPhase.DOWNLOAD))
        send(RmbtEvent.Progress(TestPhase.DOWNLOAD, fraction = 1.0, mbps = 48.0))

        send(RmbtEvent.Phase(TestPhase.UP_PRETEST))

        val state = viewModel.uiState.value
        assertEquals(MeasurementState.UPLOAD, state.state)
        assertEquals(48_000_000L, state.downloadBps)
        assertEquals(0L, state.liveSpeedBps)
        assertTrue(state.chart.isEmpty())
        viewModel.cancel()
    }

    @Test
    fun `a finished test is stored and opens the result`() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        send(RmbtEvent.Finished(RESULT))

        assertEquals(MeasurementOutcome.Finished("test-1"), viewModel.outcome.value)
        val state = viewModel.uiState.value
        assertEquals(MeasurementState.FINISH, state.state)
        assertEquals(100, state.progress)
        assertEquals(45_200_000L, state.downloadBps)
        assertEquals(12_100_000L, state.uploadBps)
        assertEquals(18_000_000L, state.pingNanos)

        assertEquals("client-2", store.clientUuid)
        val record = store.records.single()
        assertEquals("test-1", record.testUuid)
        assertEquals("open-1", record.openTestUuid)
        assertEquals(NOW, record.timeMillis)
        assertEquals(45.2, record.downloadMbps, 0.0)
        assertEquals(12.1, record.uploadMbps, 0.0)
        assertEquals(18.0, record.pingMs, 0.0)
        assertEquals("Dushanbe", record.serverName)
        assertEquals(NetworkKind.WIFI, record.network)
        assertEquals("Home", record.networkName)
        assertEquals(3, record.signalLevel)
    }

    @Test
    fun `the location found during the test is stored with it`() = runTest {
        val point = GeoPoint(latitude = 38.5598, longitude = 68.787, accuracyMeters = 12f)
        newViewModel(locate = { point })
        runCurrent()

        send(RmbtEvent.Finished(RESULT))

        assertEquals(point, store.records.single().location)
    }

    @Test
    fun `a location that never comes holds the result back only briefly`() = runTest {
        val viewModel = newViewModel(locate = { awaitCancellation() })
        runCurrent()

        send(RmbtEvent.Finished(RESULT))
        assertTrue(store.records.isEmpty())

        advanceTimeBy(3_001)
        runCurrent()

        assertNull(store.records.single().location)
        assertEquals(MeasurementOutcome.Finished("test-1"), viewModel.outcome.value)
    }

    @Test
    fun `a failing location lookup stores the test without a point`() = runTest {
        val viewModel = newViewModel(locate = { error("no play services") })
        runCurrent()

        send(RmbtEvent.Finished(RESULT))

        assertNull(store.records.single().location)
        assertEquals(MeasurementOutcome.Finished("test-1"), viewModel.outcome.value)
    }

    @Test
    fun `a failed test reports the error and stores nothing`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        val error = IllegalStateException("control server unreachable")

        send(RmbtEvent.Failed(error))

        assertEquals(MeasurementState.ERROR, viewModel.uiState.value.state)
        assertSame(error, (viewModel.outcome.value as MeasurementOutcome.Failed).error)
        assertTrue(store.records.isEmpty())
        assertEquals("client-1", store.clientUuid)
    }

    @Test
    fun `cancel stops the test and ignores later events`() = runTest {
        val viewModel = newViewModel()
        runCurrent()

        viewModel.cancel()
        send(RmbtEvent.Finished(RESULT))

        assertEquals(MeasurementState.ABORTED, viewModel.uiState.value.state)
        assertNull(viewModel.outcome.value)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun `cancel after the test finished keeps the result`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        send(RmbtEvent.Finished(RESULT))

        // E.g. the cancel dialog confirmed just after the result arrived.
        viewModel.cancel()

        assertEquals(MeasurementState.FINISH, viewModel.uiState.value.state)
        assertEquals(MeasurementOutcome.Finished("test-1"), viewModel.outcome.value)
    }

    @Test
    fun `cancel after the test failed keeps the error`() = runTest {
        val viewModel = newViewModel()
        runCurrent()
        send(RmbtEvent.Failed(IllegalStateException("down")))

        viewModel.cancel()

        assertEquals(MeasurementState.ERROR, viewModel.uiState.value.state)
    }

    private fun TestScope.newViewModel(locate: suspend () -> GeoPoint? = { null }) = MeasurementViewModel(
        store = store,
        startNetwork = wifi,
        startTest = { config ->
            startedWith = config
            events.receiveAsFlow()
        },
        locate = locate,
        elapsedRealtime = { testScheduler.currentTime },
        now = { NOW },
    )

    private fun TestScope.send(event: RmbtEvent) {
        events.trySend(event)
        runCurrent()
    }

    private class FakeStore(var clientUuid: String?) : TestRecordSink {
        val records = mutableListOf<TestRecord>()

        override suspend fun clientUuid(): String? = clientUuid

        override suspend fun saveClientUuid(uuid: String) {
            clientUuid = uuid
        }

        override suspend fun add(record: TestRecord) {
            records += record
        }
    }

    private companion object {
        const val NOW = 1_764_050_400_000L
        val RESULT = RmbtResult(
            testUuid = "test-1",
            openTestUuid = "open-1",
            clientUuid = "client-2",
            downloadMbps = 45.2,
            uploadMbps = 12.1,
            pingMs = 18.0,
            serverName = "Dushanbe",
        )
    }
}
