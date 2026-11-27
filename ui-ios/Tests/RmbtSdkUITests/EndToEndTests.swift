import Darwin
import Foundation
import Testing
@testable import RmbtSdkUI

/// Runs a real test against the default Control Server through the SDK core, as the test screen does.
/// Off by default (network, ~30 s): `TEST_RUNNER_RMBT_E2E=1 xcodebuild test ...`.
@MainActor
@Suite(.enabled(if: ProcessInfo.processInfo.environment["RMBT_E2E"] != nil))
struct EndToEndTests {

    @Test(.timeLimit(.minutes(2)))
    func coreRunsATestAndReportsInOrderOnTheMainThread() async throws {
        var events: [SpeedTestEvent] = []
        var offMain = 0
        let baseline = Self.footprintMB()
        var peak = baseline
        let result = try await KmpSpeedTestEngine().run(
            SpeedTestSettings(controlUrl: RmbtUI.config.controlUrl, apiKey: "ios-ui-e2e", clientUuid: nil, tag: nil, networkType: 99)
        ) { event in
            if !Thread.isMainThread { offMain += 1 }
            events.append(event)
            peak = max(peak, Self.footprintMB())
        }
        let phases = events.compactMap { if case .phase(let phase) = $0 { return phase } else { return nil } }
        print("E2E", result, "phases:", phases.map(\.rawValue), "events:", events.count,
              "memory MB: baseline", baseline, "peak", peak)

        #expect(!result.testUuid.isEmpty)
        #expect(result.downloadMbps > 0)
        #expect(result.uploadMbps > 0)
        #expect(result.pingMs > 0)
        #expect(offMain == 0)
        #expect(phases.first == .initializing)
        #expect(phases.last == .submit)
        #expect(events.contains { if case .progress(.download, _, _) = $0 { return true } else { return false } })
        // iOS kills an app over its memory limit (MyTelecom on an iPhone 16 Pro Max: 3376 MB, reached during the
        // upload when the WebSocket queue had no bound); the test itself needs a few MB of buffers.
        #expect(peak - baseline < 200)
    }

    /// What iOS counts against the app's memory limit (`phys_footprint`), in MB.
    private static func footprintMB() -> Int {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? Int(info.phys_footprint / 1_048_576) : 0
    }

    @Test(.timeLimit(.minutes(1)))
    func anUnreachableServerFailsWithAnErrorInsteadOfCrashing() async {
        await #expect(throws: (any Error).self) {
            try await KmpSpeedTestEngine().run(
                SpeedTestSettings(controlUrl: "https://127.0.0.1:9/RMBTControlServer", apiKey: nil, clientUuid: nil, tag: nil, networkType: 99)
            ) { _ in }
        }
    }

    @Test(.timeLimit(.minutes(1)))
    func cancellingStopsTheTest() async throws {
        let task = Task { @MainActor in
            try await KmpSpeedTestEngine().run(
                SpeedTestSettings(controlUrl: RmbtUI.config.controlUrl, apiKey: "ios-ui-e2e", clientUuid: nil, tag: nil, networkType: 99)
            ) { _ in }
        }
        try await Task.sleep(nanoseconds: 3_000_000_000)
        let started = Date()
        task.cancel()
        let outcome = await task.result
        #expect(Date().timeIntervalSince(started) < 5)
        if case .success = outcome { Issue.record("a cancelled test must not succeed") }
    }
}
