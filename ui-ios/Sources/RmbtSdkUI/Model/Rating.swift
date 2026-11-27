import SwiftUI

/// Rating of a measured value (open-rmbt-ios `ResultRating`). The app gets it from the server; the SDK rates
/// locally with the thresholds of the apps' live icons, as the Android UI SDK does.
enum Rating: Int, CaseIterable, Comparable {
    case bad = 1
    case normal = 2
    case good = 3
    case excellent = 4

    static func < (lhs: Rating, rhs: Rating) -> Bool { lhs.rawValue < rhs.rawValue }

    /// Download: below 5 Mbit/s bad, 10 normal, 100 good, otherwise excellent; nil when nothing was measured.
    static func download(mbps: Double) -> Rating? { speed(mbps, normal: 5, good: 10, excellent: 100) }

    /// Upload: below 2.5 Mbit/s bad, 5 normal, 50 good, otherwise excellent.
    static func upload(mbps: Double) -> Rating? { speed(mbps, normal: 2.5, good: 5, excellent: 50) }

    /// Ping: 75 ms and more bad, from 25 normal, from 10 good, below 10 excellent.
    static func ping(ms: Double) -> Rating? {
        switch ms {
        case ...0: return nil
        case 75...: return .bad
        case 25...: return .normal
        case 10...: return .good
        default: return .excellent
        }
    }

    /// The connection is only as good as its weakest part: the lowest rating, nil when none is rated.
    static func overall(_ ratings: Rating?...) -> Rating? {
        ratings.compactMap { $0 }.min()
    }

    private static func speed(_ mbps: Double, normal: Double, good: Double, excellent: Double) -> Rating? {
        switch mbps {
        case ...0: return nil
        case ..<normal: return .bad
        case ..<good: return .normal
        case ..<excellent: return .good
        default: return .excellent
        }
    }

    var name: String {
        switch self {
        case .excellent: return L10n.tr("result.rating.excellent")
        case .good: return L10n.tr("result.rating.good")
        case .normal: return L10n.tr("result.rating.normal")
        case .bad: return L10n.tr("result.rating.bad")
        }
    }

    var verdictTitle: String {
        switch self {
        case .excellent: return L10n.tr("result.verdict.excellent")
        case .good: return L10n.tr("result.verdict.good")
        case .normal: return L10n.tr("result.verdict.normal")
        case .bad: return L10n.tr("result.verdict.bad")
        }
    }

    var verdictDescription: String {
        switch self {
        case .excellent: return L10n.tr("result.verdict.excellent.description")
        case .good: return L10n.tr("result.verdict.good.description")
        case .normal: return L10n.tr("result.verdict.normal.description")
        case .bad: return L10n.tr("result.verdict.bad.description")
        }
    }

    /// `UIColor.byResultClass` of the app.
    var color: Color {
        switch self {
        case .bad: return Color(red: 0xF5 / 255, green: 0x00 / 255, blue: 0x1C / 255)
        case .normal: return Color(red: 0xFF / 255, green: 0xBA / 255, blue: 0x00 / 255)
        case .good: return Color(red: 0x2E / 255, green: 0x9B / 255, blue: 0xEE / 255)
        case .excellent: return Color(red: 0x0D / 255, green: 0x5D / 255, blue: 0x9B / 255)
        }
    }
}
