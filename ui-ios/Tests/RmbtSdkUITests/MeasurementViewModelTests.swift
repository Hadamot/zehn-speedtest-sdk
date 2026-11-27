import Foundation
import Testing
@testable import RmbtSdkUI

@MainActor
struct MeasurementViewModelTests {

    private let wifi = NetworkState(isConnected: true, kind: .wifi)

    @Test func passesTheStoredClientAndTheNetworkToTheCore() async {
        let engine = FakeEngine(result: .success(.sample))
        let store = FakeSink(clientUuid: "known-client")
        let viewModel = MeasurementViewModel(network: wifi, engine: engine, store: store)

        await viewModel.run()

        #expect(engine.settings?.clientUuid == "known-client")
        #expect(engine.settings?.networkType == 99)
    }

    @Test func storesTheFinishedTestAndTheAssignedClient() async throws {
        let engine = FakeEngine(result: .success(.sample))
        let store = FakeSink(clientUuid: nil)
        let cellular = NetworkState(isConnected: true, kind: .cellular, technology: "4G")
        let location = GeoPoint(lat: 38.56, lon: 68.79, accuracy: 20)
        let viewModel = MeasurementViewModel(
            network: cellular, engine: engine, store: store,
            locate: { location },
            now: { Date(timeIntervalSince1970: 1_764_150_000) }
        )

        await viewModel.run()

        let record = try #require(store.records.first)
        #expect(store.clientUuid == "assigned-client")
        #expect(record.testUuid == "test-1")
        #expect(record.time == 1_764_150_000_000)
        #expect(record.network == .cellular)
        #expect(record.technology == "4G")
        #expect(record.location == location)
        #expect(viewModel.state.outcome == .finished(record))
        #expect(viewModel.state.pingMs == 18)
        #expect(viewModel.state.totalPercent == 100)
    }

    @Test func showsLiveSpeedAndKeepsTheDownloadOnceTheUploadStarts() async {
        let engine = FakeEngine(result: .success(.sample))
        engine.events = [
            .phase(.initializing),
            .phase(.ping),
            .phase(.download),
            .progress(.download, fraction: 0.5, mbps: 40),
            .progress(.download, fraction: 1, mbps: 44),
            .phase(.upPretest),
            .progress(.upPretest, fraction: 0.5, mbps: 3),
            .phase(.upload),
            .progress(.upload, fraction: 0.4, mbps: 11),
        ]
        var seen: [MeasurementUiState] = []
        let viewModel = MeasurementViewModel(network: wifi, engine: engine, store: FakeSink(clientUuid: nil))
        engine.afterEvent = { seen.append(viewModel.state) }

        await viewModel.run()

        let download = seen[4]
        #expect(download.step == .download)
        #expect(download.liveMbps == 44)
        #expect(download.chart.count == 2)
        let upPretest = seen[6]
        #expect(upPretest.step == .upload)
        #expect(upPretest.downloadMbps == 44)
        // The pretest's progress is not the upload's.
        #expect(upPretest.liveMbps == 0)
        let upload = seen[8]
        #expect(upload.uploadMbps == 11)
        #expect(upload.totalPercent == 82)
    }

    @Test func aFailedTestAsksToRetryAndStoresNothing() async {
        let engine = FakeEngine(result: .failure(NSError(domain: "kotlin", code: 1)))
        let store = FakeSink(clientUuid: nil)
        let viewModel = MeasurementViewModel(network: wifi, engine: engine, store: store)

        await viewModel.run()

        #expect(viewModel.state.outcome == .failed)
        #expect(store.records.isEmpty)
    }

    @Test func aRetryStartsFromTheBeginning() async {
        let engine = FakeEngine(result: .failure(NSError(domain: "kotlin", code: 1)))
        let viewModel = MeasurementViewModel(network: wifi, engine: engine, store: FakeSink(clientUuid: nil))
        await viewModel.run()

        engine.result = .success(.sample)
        await viewModel.run()

        #expect(engine.runs == 2)
        if case .finished = viewModel.state.outcome {} else { Issue.record("expected a finished test") }
    }

    @Test func leavingTheScreenIsNotAnError() async {
        let engine = FakeEngine(result: .failure(CancellationError()))
        let store = FakeSink(clientUuid: nil)
        let viewModel = MeasurementViewModel(network: wifi, engine: engine, store: store)

        await viewModel.run()

        #expect(viewModel.state.outcome == nil)
        #expect(store.records.isEmpty)
    }

    @Test func aMissingLocationDoesNotHoldTheResultBack() async throws {
        let engine = FakeEngine(result: .success(.sample))
        let store = FakeSink(clientUuid: nil)
        let viewModel = MeasurementViewModel(
            network: wifi, engine: engine, store: store,
            // Like LocationSource: no fix for a long time, nil once cancelled.
            locate: {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                return Task.isCancelled ? nil : GeoPoint(lat: 0, lon: 0, accuracy: nil)
            }
        )
        let started = Date()

        await viewModel.run()

        let record = try #require(store.records.first)
        #expect(record.location == nil)
        #expect(Date().timeIntervalSince(started) < MeasurementViewModel.locationWait + 2)
    }
}

@MainActor
private final class FakeEngine: SpeedTestEngine {
    var result: Result<SpeedTestResult, Error>
    var events: [SpeedTestEvent] = []
    var afterEvent: () -> Void = {}
    private(set) var settings: SpeedTestSettings?
    private(set) var runs = 0

    init(result: Result<SpeedTestResult, Error>) {
        self.result = result
    }

    func run(_ settings: SpeedTestSettings, onEvent: @escaping @MainActor (SpeedTestEvent) -> Void) async throws -> SpeedTestResult {
        self.settings = settings
        runs += 1
        for event in events {
            onEvent(event)
            afterEvent()
        }
        return try result.get()
    }
}

@MainActor
private final class FakeSink: TestRecordSink {
    var clientUuid: String?
    private(set) var records: [TestRecord] = []

    init(clientUuid: String?) {
        self.clientUuid = clientUuid
    }

    func add(_ record: TestRecord) {
        records.insert(record, at: 0)
    }
}

private extension SpeedTestResult {
    static let sample = SpeedTestResult(
        testUuid: "test-1", openTestUuid: "O-1", clientUuid: "assigned-client",
        downloadMbps: 45.2, uploadMbps: 12.1, pingMs: 18, serverName: "Dushanbe"
    )
}
