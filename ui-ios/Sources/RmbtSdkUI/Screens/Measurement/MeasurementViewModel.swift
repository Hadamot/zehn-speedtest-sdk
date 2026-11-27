import Foundation

/// A live speed point of the running download or upload, for the chart.
struct SpeedPoint: Equatable {
    let fraction: Double
    let mbps: Double
}

/// How a test ended.
enum MeasurementOutcome: Equatable {
    case finished(TestRecord)
    case failed
}

/// What the test screen shows.
struct MeasurementUiState: Equatable {
    var step: TestStep = .connecting
    /// Progress of the current step, 0...1.
    var stepFraction: Double = 0
    /// Live speed of the running download or upload.
    var liveMbps: Double = 0
    var downloadMbps: Double?
    var uploadMbps: Double?
    /// The core reports the ping only with the result.
    var pingMs: Double?
    var chart: [SpeedPoint] = []
    var outcome: MeasurementOutcome?

    /// "54" of "54 %": the whole test.
    var totalPercent: Int {
        let range = step.percentRange
        return Int(range.lowerBound + (range.upperBound - range.lowerBound) * stepFraction)
    }

    /// Filled part of the progress gauge, 0...1.
    var progressGauge: Double {
        let range = step.gaugeRange
        return range.lowerBound + (range.upperBound - range.lowerBound) * stepFraction
    }

    var speedGauge: Double { SpeedGauge.value(mbps: liveMbps) }

    /// Download and upload show their live speed and gauge; connecting and ping don't.
    var isTransferring: Bool { step == .download || step == .upload }
}

/// Runs one test for the test screen. The view starts it with `.task` (re-entry is ignored); leaving the screen
/// cancels the task and with it the test.
@MainActor
final class MeasurementViewModel: ObservableObject {

    @Published private(set) var state = MeasurementUiState()

    /// The network when the test started; stored with the test.
    let network: NetworkState

    private let engine: SpeedTestEngine
    private let store: TestRecordSink
    private let locate: @MainActor () async -> GeoPoint?
    private let uptime: () -> TimeInterval
    private let now: () -> Date
    private var isRunning = false
    private var ticker: Task<Void, Never>?

    /// - Parameters:
    ///   - locate: the device location or nil, asked once while the test runs.
    ///   - uptime: monotonic clock for the progress by time, in seconds.
    ///   - now: wall clock for the stored test time.
    init(
        network: NetworkState,
        engine: SpeedTestEngine,
        store: TestRecordSink,
        locate: @escaping @MainActor () async -> GeoPoint? = { nil },
        uptime: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
        now: @escaping () -> Date = Date.init
    ) {
        self.network = network
        self.engine = engine
        self.store = store
        self.locate = locate
        self.uptime = uptime
        self.now = now
    }

    func run() async {
        guard !isRunning else { return }
        isRunning = true
        defer {
            isRunning = false
            ticker?.cancel()
        }
        state = MeasurementUiState()
        enter(.connecting)
        let location = Task { await locate() }
        defer { location.cancel() }
        let config = RmbtUI.config
        let settings = SpeedTestSettings(
            controlUrl: config.controlUrl,
            apiKey: config.apiKey,
            clientUuid: store.clientUuid,
            tag: config.tag,
            networkType: (network.kind ?? .other).rmbtNetworkType
        )
        do {
            let result = try await engine.run(settings) { [weak self] event in self?.handle(event) }
            ticker?.cancel()
            let point = await Self.value(of: location, waitingAtMost: Self.locationWait)
            finish(with: result, location: point)
        } catch {
            ticker?.cancel()
            // Leaving the screen cancels the test; the core then fails with its own cancellation error.
            guard !Task.isCancelled, !(error is CancellationError) else { return }
            state.outcome = .failed
        }
    }

    private func handle(_ event: SpeedTestEvent) {
        switch event {
        case .phase(let phase):
            switch phase {
            // The last live value stays as the result of the step until the test ends.
            case .upPretest where state.liveMbps > 0:
                state.downloadMbps = state.liveMbps
            case .submit:
                state.stepFraction = 1
                if state.liveMbps > 0 { state.uploadMbps = state.liveMbps }
            default:
                break
            }
            let step = TestStep(phase)
            if step != state.step { enter(step) }
        case let .progress(phase, fraction, mbps):
            // Pretests report progress too; only the download and upload are shown.
            guard phase == .download || phase == .upload, TestStep(phase) == state.step else { return }
            state.stepFraction = min(max(fraction, 0), 1)
            state.liveMbps = mbps
            if phase == .download { state.downloadMbps = mbps } else { state.uploadMbps = mbps }
            state.chart.append(SpeedPoint(fraction: state.stepFraction, mbps: mbps))
        }
    }

    /// The core reports progress only while it transfers data; connecting and the pings advance by time,
    /// up to 95 %, so the gauge keeps moving.
    private func enter(_ step: TestStep) {
        ticker?.cancel()
        state.step = step
        state.stepFraction = 0
        state.liveMbps = 0
        state.chart = []
        guard let expected = step.expectedDuration else { return }
        let start = uptime()
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, self.state.step == step else { return }
                self.state.stepFraction = min((self.uptime() - start) / expected, Self.timedProgressMax)
                try? await Task.sleep(nanoseconds: Self.tickNanos)
            }
        }
    }

    private func finish(with result: SpeedTestResult, location: GeoPoint?) {
        store.clientUuid = result.clientUuid
        let record = TestRecord(
            testUuid: result.testUuid,
            openTestUuid: result.openTestUuid,
            time: Int64((now().timeIntervalSince1970 * 1000).rounded()),
            timezone: TimeZone.current.identifier,
            download: result.downloadMbps,
            upload: result.uploadMbps,
            ping: result.pingMs,
            server: result.serverName,
            network: network.kind ?? .other,
            networkName: nil,
            signalLevel: nil,
            location: location,
            technology: network.technology
        )
        store.add(record)
        state.step = .upload
        state.stepFraction = 1
        state.liveMbps = 0
        state.downloadMbps = result.downloadMbps
        state.uploadMbps = result.uploadMbps
        state.pingMs = result.pingMs
        state.outcome = .finished(record)
    }

    /// The test takes far longer than a location fix; a missing one never holds the result back for long.
    private static func value(of task: Task<GeoPoint?, Never>, waitingAtMost seconds: TimeInterval) async -> GeoPoint? {
        let timeout = Task {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            task.cancel()
        }
        defer { timeout.cancel() }
        return await task.value
    }

    private static let tickNanos: UInt64 = 100_000_000
    private static let timedProgressMax = 0.95
    static let locationWait: TimeInterval = 3
}
