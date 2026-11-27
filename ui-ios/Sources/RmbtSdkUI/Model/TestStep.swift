import Foundation

/// Phases the SDK core reports while a test runs (`TestPhase`).
enum SpeedTestPhase: String, CaseIterable {
    case initializing = "INIT"
    case downPretest = "DOWN_PRETEST"
    case ping = "PING"
    case download = "DOWNLOAD"
    case upPretest = "UP_PRETEST"
    case upload = "UPLOAD"
    case submit = "SUBMIT"
}

/// The steps of a running test as the user sees them ("Step 3 of 4 · Download") with what each measures
/// (open-rmbt-ios `TestPhaseStep`). The pretests belong to the step they prepare; submitting ends the upload.
enum TestStep: Int, CaseIterable {
    case connecting = 1
    case ping
    case download
    case upload

    static let total = 4

    init(_ phase: SpeedTestPhase) {
        switch phase {
        case .initializing, .downPretest: self = .connecting
        case .ping: self = .ping
        case .download: self = .download
        case .upPretest, .upload, .submit: self = .upload
        }
    }

    var name: String {
        switch self {
        case .connecting: return L10n.tr("test.step.connecting")
        case .ping: return L10n.tr("metric.ping")
        case .download: return L10n.tr("metric.download")
        case .upload: return L10n.tr("metric.upload")
        }
    }

    var hint: String {
        switch self {
        case .connecting: return L10n.tr("test.step.connecting.hint")
        case .ping: return L10n.tr("test.step.ping.hint")
        case .download: return L10n.tr("test.step.download.hint")
        case .upload: return L10n.tr("test.step.upload.hint")
        }
    }

    /// "Step 3 of 4 · Download"
    var title: String {
        L10n.tr("test.step.title", rawValue, Self.total, name)
    }

    /// Share of the whole test in percent, before and after this step (open-rmbt-ios, without QoS).
    var percentRange: ClosedRange<Double> {
        switch self {
        case .connecting: return 0...20
        case .ping: return 20...40
        case .download: return 40...70
        case .upload: return 70...100
        }
    }

    /// Part of the progress gauge's arc this step fills; the rest of the arc belongs to QoS, which the SDK
    /// doesn't run.
    var gaugeRange: ClosedRange<Double> {
        switch self {
        case .connecting: return 0...0.10
        case .ping: return 0.10...0.23
        case .download: return 0.23...0.49
        case .upload: return 0.49...0.74
        }
    }

    /// The core reports progress only while it transfers data; connecting and the pings advance by time.
    var expectedDuration: TimeInterval? {
        switch self {
        case .connecting: return 3
        case .ping: return 1.5
        case .download, .upload: return nil
        }
    }
}

/// Position of a speed on the logarithmic speed gauge (open-rmbt-ios `RMBTSpeedLogValue`): 100 kbit/s at the
/// start, 10 Gbit/s at the end.
enum SpeedGauge {
    static func value(mbps: Double) -> Double {
        let kbps = mbps * 1000
        guard kbps >= 100 else { return 0 }
        return min(log10(kbps / 100) / 5, 1)
    }
}
