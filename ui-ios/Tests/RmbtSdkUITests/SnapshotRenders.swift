import SwiftUI
import Testing
@testable import RmbtSdkUI

/// Renders the screens to PNG files for a visual check, in light and dark and with large text.
/// Off by default: `TEST_RUNNER_RMBT_SNAPSHOTS=/path/to/folder xcodebuild test ...`.
@MainActor
@Suite(.serialized, .enabled(if: ProcessInfo.processInfo.environment["RMBT_SNAPSHOTS"] != nil))
struct SnapshotRenders {

    private let folder = URL(fileURLWithPath: ProcessInfo.processInfo.environment["RMBT_SNAPSHOTS"] ?? NSTemporaryDirectory())
    private let size = CGSize(width: 393, height: 760)

    private var record: TestRecord {
        TestRecord(
            testUuid: "1", openTestUuid: nil, time: Int64(Date().timeIntervalSince1970 * 1000), timezone: "Asia/Dushanbe",
            download: 72.8, upload: 64.8, ping: 5, server: "Tojiktelecom (Dushanbe)", network: .cellular, networkName: nil,
            signalLevel: nil, location: nil, technology: "4G"
        )
    }

    @Test(arguments: ["en", "ru", "tg"])
    func screens(language: String) throws {
        RmbtUI.configure(RmbtUiConfig(language: language))
        defer { RmbtUI.configure(RmbtUiConfig()) }
        let download = MeasurementUiState(
            step: .download, stepFraction: 0.6, liveMbps: 72.8, downloadMbps: 72.8,
            chart: (0...12).map { SpeedPoint(fraction: Double($0) / 20, mbps: 50 + Double($0) * 2) }
        )
        let cellular = NetworkState(isConnected: true, kind: .cellular, technology: "4G")
        try render(MeasurementContent(state: download, network: cellular, onClose: {}), "measurement-\(language)")
        try render(ResultDetails(record: record).frame(width: size.width).background(Color.white), "result-\(language)", height: nil)
        try render(HomeScreen(network: cellular, lastTest: record, onStart: {}, onOpenLastTest: {}), "home-\(language)")
        let older = TestRecord(testUuid: "2", openTestUuid: nil, time: record.time - 86_400_000, timezone: record.timezone,
                               download: 3.1, upload: 1.2, ping: 80, server: record.server, network: .wifi, networkName: nil,
                               signalLevel: nil, location: nil, technology: nil)
        try render(HistoryRows(records: [record, older], onOpen: { _ in }).frame(width: size.width).background(Color.white),
                   "history-\(language)", height: nil)
    }

    @Test func variants() throws {
        RmbtUI.configure(RmbtUiConfig(language: "ru"))
        defer { RmbtUI.configure(RmbtUiConfig()) }
        try render(HomeScreen(network: .offline, lastTest: nil, onStart: {}, onOpenLastTest: {}), "home-offline")
        var poor = record
        poor = TestRecord(testUuid: "2", openTestUuid: nil, time: poor.time, timezone: poor.timezone, download: 3.1, upload: 1.2,
                          ping: 80, server: poor.server, network: .wifi, networkName: nil, signalLevel: nil, location: nil, technology: nil)
        try render(ResultDetails(record: poor).frame(width: size.width).background(Color.black).environment(\.colorScheme, .dark),
                   "result-dark", height: nil)
        try render(ResultDetails(record: record).frame(width: size.width).background(Color.white)
            .environment(\.dynamicTypeSize, .accessibility3), "result-large-text", height: nil)
        try render(MeasurementContent(state: MeasurementUiState(), network: NetworkState(isConnected: true, kind: .wifi), onClose: {})
            .environment(\.dynamicTypeSize, .accessibility3), "measurement-large-text")
        try render(MeasurementContent(state: MeasurementUiState(step: .upload, stepFraction: 0.3, liveMbps: 0.6, downloadMbps: 8.4,
                                                                 uploadMbps: 0.6),
                                      network: NetworkState(isConnected: true, kind: .wifi), onClose: {}),
                   "measurement-se", width: 320, height: 568)
    }

    private func render<V: View>(_ view: V, _ name: String, width: CGFloat? = nil, height: CGFloat? = 760) throws {
        let renderer = ImageRenderer(content: view
            .frame(width: width ?? size.width, height: height)
            .fixedSize(horizontal: false, vertical: height == nil))
        renderer.scale = 2
        let image = try #require(renderer.uiImage, "nothing rendered for \(name)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try #require(image.pngData()).write(to: folder.appendingPathComponent("\(name).png"))
    }
}
