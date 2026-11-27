import Foundation

/// Kind of network a test ran on; stored by name, as on Android.
enum NetworkKind: String, Codable {
    case wifi = "WIFI"
    case cellular = "CELLULAR"
    case ethernet = "ETHERNET"
    case other = "OTHER"

    init(from decoder: Decoder) throws {
        self = NetworkKind(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .other
    }

    /// Network type code the Control Server stores with the test (the Android UI SDK's values).
    var rmbtNetworkType: Int32 {
        switch self {
        case .wifi: return 99
        case .ethernet: return 106
        case .cellular: return 105
        case .other: return 98
        }
    }
}

/// Where the device was when the test ran; `accuracy` is the radius in metres, when known.
struct GeoPoint: Codable, Equatable {
    let lat: Double
    let lon: Double
    let accuracy: Double?
}

/// One finished test as the history and the result screen show it. The JSON keys are those of the Android
/// UI SDK's history.
struct TestRecord: Codable, Equatable, Identifiable {
    let testUuid: String
    let openTestUuid: String?
    /// Milliseconds since 1970.
    let time: Int64
    /// Time zone the test ran in, e.g. "Asia/Dushanbe".
    let timezone: String
    let download: Double
    let upload: Double
    let ping: Double
    let server: String
    let network: NetworkKind
    let networkName: String?
    /// Signal level 0...4 when known; iOS doesn't tell apps the signal, so always nil here.
    let signalLevel: Int?
    let location: GeoPoint?
    /// Mobile generation when known, e.g. "4G"; not in records made on Android.
    let technology: String?

    var id: String { testUuid }

    var date: Date { Date(timeIntervalSince1970: TimeInterval(time) / 1000) }

    var timeZone: TimeZone { TimeZone(identifier: timezone) ?? .current }

    var downloadRating: Rating? { Rating.download(mbps: download) }
    var uploadRating: Rating? { Rating.upload(mbps: upload) }
    var pingRating: Rating? { Rating.ping(ms: ping) }
    var overallRating: Rating? { Rating.overall(downloadRating, uploadRating, pingRating) }

    /// "4G", "Wi-Fi", "Mobile network": the network as the result and the shared text name it.
    var networkTitle: String {
        switch network {
        case .wifi: return L10n.tr("network.wifi")
        case .cellular: return technology ?? L10n.tr("network.mobile")
        case .ethernet: return L10n.tr("network.ethernet")
        case .other: return L10n.tr("network.other")
        }
    }
}
