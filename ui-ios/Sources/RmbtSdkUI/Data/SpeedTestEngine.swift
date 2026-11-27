import Foundation
import RmbtSdk

/// What the test screen needs from a speed test; ``KmpSpeedTestEngine`` runs the SDK core, tests pass fakes.
@MainActor
protocol SpeedTestEngine {
    /// Runs one test. `onEvent` is called on the main actor, in order, before the result returns. Throws
    /// `CancellationError` when the surrounding task is cancelled.
    func run(_ settings: SpeedTestSettings, onEvent: @escaping @MainActor (SpeedTestEvent) -> Void) async throws -> SpeedTestResult
}

struct SpeedTestSettings: Equatable {
    var controlUrl: String
    var apiKey: String?
    var clientUuid: String?
    var tag: String?
    var networkType: Int32
}

enum SpeedTestEvent: Equatable {
    case phase(SpeedTestPhase)
    /// `fraction` of the running download or upload, its live speed.
    case progress(SpeedTestPhase, fraction: Double, mbps: Double)
}

struct SpeedTestResult: Equatable {
    var testUuid: String
    var openTestUuid: String?
    var clientUuid: String
    var downloadMbps: Double
    var uploadMbps: Double
    var pingMs: Double
    var serverName: String
}

/// The SDK core (`RmbtClient`). Its callbacks arrive on a background thread; they are moved to the main
/// queue, which keeps their order, and the result follows the last event the same way.
@MainActor
struct KmpSpeedTestEngine: SpeedTestEngine {

    func run(_ settings: SpeedTestSettings, onEvent: @escaping @MainActor (SpeedTestEvent) -> Void) async throws -> SpeedTestResult {
        let client = RmbtClient(config: RmbtConfig(
            controlUrl: settings.controlUrl,
            apiKey: settings.apiKey,
            clientUuid: settings.clientUuid,
            // WebSocket on iOS: the core can't do TLS over TCP sockets on Kotlin/Native.
            transport: Platform.shared.defaultTransport,
            threads: nil,
            durationSec: nil,
            tag: settings.tag,
            networkType: settings.networkType,
            clientInfo: Platform.shared.clientInfo,
            measurementHost: nil
        ))
        // A client per test, closed with it: it owns the sockets' selector and an HTTP client.
        defer { client.close() }
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                client.run(
                    onEvent: { event in
                        guard let event = Self.map(event) else { return }
                        DispatchQueue.main.async { MainActor.assumeIsolated { onEvent(event) } }
                    },
                    completionHandler: { result, error in
                        let outcome: Result<SpeedTestResult, Error> = result.map { .success(Self.map($0)) }
                            ?? .failure(error ?? CancellationError())
                        DispatchQueue.main.async { continuation.resume(with: outcome) }
                    }
                )
            }
        } onCancel: {
            client.cancel()
        }
    }

    private nonisolated static func map(_ event: RmbtEvent) -> SpeedTestEvent? {
        if let event = event as? RmbtEventPhase, let phase = SpeedTestPhase(rawValue: event.phase.name) {
            return .phase(phase)
        }
        if let event = event as? RmbtEventProgress, let phase = SpeedTestPhase(rawValue: event.phase.name) {
            return .progress(phase, fraction: event.fraction, mbps: event.mbps)
        }
        // Finished and Failed come as the result or the error of `run`.
        return nil
    }

    private nonisolated static func map(_ result: RmbtResult) -> SpeedTestResult {
        SpeedTestResult(
            testUuid: result.testUuid,
            openTestUuid: result.openTestUuid,
            clientUuid: result.clientUuid,
            downloadMbps: result.downloadMbps,
            uploadMbps: result.uploadMbps,
            pingMs: result.pingMs,
            serverName: result.serverName
        )
    }
}
