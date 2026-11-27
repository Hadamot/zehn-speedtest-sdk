import MapKit
import SwiftUI

/// The result of a test from the device history, by the UUID ``RmbtStartView`` or ``RmbtHistoryListView``
/// gave; push it in the app's `NavigationStack`. It shows a navigation bar with the date and a share button.
public struct RmbtResultView: View {
    private let testUuid: String
    @ObservedObject private var history = HistoryStore.shared

    public init(testUuid: String) {
        self.testUuid = testUuid
    }

    public var body: some View {
        if let record = history.record(testUuid) {
            ResultScreen(record: record)
        } else {
            // Not reachable from the SDK's own screens: a result is only opened for a stored test.
            Text(ValueFormat.dash).foregroundColor(.secondary)
        }
    }
}

/// The result of one test, one to one with the Android UI SDK (`rmbt_activity_results.xml`, `ResultActivity`):
/// the network, the verdict with its meter, download, upload and ping with their ratings, the network details
/// and where the test ran on a map. White, like the Android screen.
struct ResultScreen: View {
    let record: TestRecord

    var body: some View {
        ScrollView {
            ResultDetails(record: record)
        }
        .background(Color.white.ignoresSafeArea())
        .environment(\.colorScheme, .light)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.white, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.light, for: .navigationBar)
        .tint(AndroidColor.textGray)
        .toolbar {
            ToolbarItem(placement: .principal) { ResultTitle(record: record) }
            ToolbarItem(placement: .navigationBarTrailing) {
                ShareLink(item: ResultShareText.text(for: record), subject: Text(L10n.tr("share.title", L10n.appName))) {
                    RmbtImage.named("nav_share").resizable().frame(width: 24, height: 24)
                }
                .accessibilityLabel(L10n.tr("result.share"))
            }
        }
    }
}

/// `testTime`: "📅 20.11.18 🕒 12:00:45" with the Android calendar and clock icons, in the test's time zone.
private struct ResultTitle: View {
    let record: TestRecord

    @ScaledMetric(relativeTo: .title3) private var iconSize: CGFloat = 20

    var body: some View {
        let date = HistoryTimeFormat.fixed("dd.MM.yy", record.timeZone).string(from: record.date)
        let time = HistoryTimeFormat.fixed("HH:mm:ss", record.timeZone).string(from: record.date)
        HStack(spacing: 4) {
            RmbtImage.named("title_calendar").resizable().frame(width: iconSize, height: iconSize)
            Text(date)
            RmbtImage.named("title_clock").resizable().frame(width: iconSize, height: iconSize)
                .padding(.leading, 4)
            Text(time)
        }
        .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
        .foregroundColor(AndroidColor.textPrimary)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L10n.tr("result.a11y.time", "\(date), \(time)"))
        .accessibilityAddTraits(.isHeader)
    }
}

/// The content of the result screen, without its scroll view and navigation bar.
struct ResultDetails: View {
    let record: TestRecord

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            network
            if let rating = record.overallRating {
                VerdictCard(rating: rating)
                    .padding(.top, 16)
            }
            speeds
                .padding(.horizontal, 16)
                .padding(.top, 16)
            value(label: L10n.tr("metric.ping"), icon: "small_ping-\(record.pingRating?.rawValue ?? 0)",
                  text: ValueFormat.withUnit(ValueFormat.ping(ms: record.ping), L10n.tr("unit.ping")),
                  rating: record.pingRating, meaning: L10n.tr("result.hint.ping"))
                .padding(.horizontal, 16)
                .padding(.top, 8)
            details
            location
        }
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Network

    /// `networkTypeText` (the kind, in capitals), `networkNameText` (the network's name; iOS knows the mobile
    /// generation only) and the network icon on the right.
    private var network: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(record.networkKindLabel.uppercased())
                    .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                    .foregroundColor(Color.black.opacity(0.4))
                if let name = record.networkDisplayName {
                    Text(name.uppercased())
                        .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
                        .foregroundColor(AndroidColor.textPrimary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HistorySignalIcon(network: record.network)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .accessibilityElement(children: .combine)
    }

    // MARK: Values

    /// "Down" and "Up" side by side (one under the other at accessibility sizes), each with its value and rating.
    private var speeds: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 0))
        return layout {
            value(label: L10n.tr("result.down"), icon: "small_download-\(record.downloadRating?.rawValue ?? 0)",
                  text: ValueFormat.withUnit(ValueFormat.speed(mbps: record.download), L10n.tr("unit.speed")),
                  rating: record.downloadRating, meaning: nil)
            value(label: L10n.tr("result.up"), icon: "small_upload-\(record.uploadRating?.rawValue ?? 0)",
                  text: ValueFormat.withUnit(ValueFormat.speed(mbps: record.upload), L10n.tr("unit.speed")),
                  rating: record.uploadRating, meaning: nil)
        }
    }

    /// A label (bold, grey), the value with the rating icon sized to the text, and the coloured rating word with
    /// what the value means (`setResultHint`).
    private func value(label: String, icon: String, text: String, rating: Rating?, meaning: String?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(RmbtFont.roboto(16, .bold, relativeTo: .callout))
                .foregroundColor(AndroidColor.textGray60)
            ValueLine(icon: icon, text: text)
            if let hint = ResultHint.text(rating: rating, meaning: meaning) {
                hint
                    .font(RmbtFont.roboto(12, relativeTo: .caption))
                    .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    // MARK: Details

    /// "Further details", then "Network": the kind of network and the measurement server.
    private var details: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L10n.tr("result.details"))
                .font(RmbtFont.roboto(16, .medium, relativeTo: .callout))
                .foregroundColor(AndroidColor.textGray60)
                .frame(minHeight: 56)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .accessibilityAddTraits(.isHeader)
            SectionTitle(text: L10n.tr("result.network.title"))
            DetailRow(title: L10n.tr("result.network.type"), value: record.networkKindLabel)
            DetailRow(title: L10n.tr("result.server"), value: record.server.isEmpty ? ValueFormat.dash : record.server)
        }
    }

    // MARK: Location

    /// "Location": the map with the point and the accuracy circle (tapping it opens Maps), then the coordinates;
    /// without a location a line saying how to get one.
    private var location: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionTitle(text: L10n.tr("result.location"))
                .padding(.top, 8)
            if let point = record.location {
                Button { ResultMap.openInMaps(point) } label: {
                    ResultMap(point: point)
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .background(RmbtColor.lastTestCard)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .accessibilityLabel(L10n.tr("result.location.map"))
            }
            Text(locationText)
                .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                .foregroundColor(AndroidColor.textGray60)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.top, 8)
        }
    }

    /// "38.55980, 68.78700 · ±12 m": a dot and five decimals, as maps apps take them.
    private var locationText: String {
        guard let point = record.location else { return L10n.tr("result.location.unknown") }
        var parts = [String(format: "%.5f, %.5f", locale: Locale(identifier: "en_US_POSIX"), point.lat, point.lon)]
        if let accuracy = point.accuracy, accuracy.rounded() >= 1 {
            parts.append(L10n.tr("result.location.accuracy", Int(accuracy.rounded())))
        }
        return parts.joined(separator: " · ")
    }
}

/// A value with the rating icon at the text's size (`setStartIconSizedToText`).
private struct ValueLine: View {
    let icon: String
    let text: String

    @ScaledMetric(relativeTo: .title3) private var iconSize: CGFloat = 20

    var body: some View {
        HStack(spacing: 2) {
            RmbtImage.named(icon).resizable().frame(width: iconSize, height: iconSize)
            Text(text)
                .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
                .foregroundColor(AndroidColor.textPrimary)
                .lineLimit(2)
        }
    }
}

/// The hint under a value: the rating word in its colour, then " · " and what the value means.
enum ResultHint {
    static func text(rating: Rating?, meaning: String?) -> Text? {
        var parts: [Text] = []
        if let rating { parts.append(Text(rating.name).foregroundColor(rating.androidColor)) }
        if let meaning, !meaning.isEmpty { parts.append(Text(meaning).foregroundColor(AndroidColor.textGray60)) }
        guard let first = parts.first else { return nil }
        return parts.dropFirst().reduce(first) { $0 + Text(" · ").foregroundColor(AndroidColor.textGray60) + $1 }
    }
}

/// The overall verdict on a card tinted with the rating colour (12 %), with the meter and the description
/// (`showVerdict`).
private struct VerdictCard: View {
    let rating: Rating

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(rating.verdictTitle)
                .font(RmbtFont.roboto(16, .bold, relativeTo: .callout))
                .foregroundColor(rating.androidColor)
                .padding(.top, 8)
                .accessibilityAddTraits(.isHeader)
            RatingMeter(rating: rating)
                .frame(width: 160)
            Text(rating.verdictDescription)
                .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                .foregroundColor(AndroidColor.textGray60)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 8)
        }
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(rating.androidColor.opacity(0x1F / 255)))
        .padding(.horizontal, 8)
        .accessibilityElement(children: .combine)
    }
}

/// `RatingMeterView`: four segments, those up to the rating filled in its colour, filling up from the left in
/// 450 ms when shown.
struct RatingMeter: View {
    let rating: Rating

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var filled: Double = 0

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<4, id: \.self) { index in
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.black.opacity(0x1F / 255))
                        Capsule().fill(rating.androidColor)
                            .frame(width: geo.size.width * min(max(filled - Double(index), 0), 1))
                    }
                }
            }
        }
        .frame(height: 6)
        .accessibilityElement()
        .accessibilityLabel(rating.name)
        .onAppear {
            guard !reduceMotion else {
                filled = Double(rating.rawValue)
                return
            }
            withAnimation(.easeOut(duration: 0.45)) { filled = Double(rating.rawValue) }
        }
    }
}

/// A bold section title on a 56 pt row ("Network", "Location").
private struct SectionTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(RmbtFont.roboto(16, .bold, relativeTo: .callout))
            .foregroundColor(AndroidColor.textPrimary)
            .frame(minHeight: 56)
            .padding(.horizontal, 16)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A label and its value on the right.
private struct DetailRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(title)
            Spacer(minLength: 0)
            Text(value).multilineTextAlignment(.trailing)
        }
        .font(RmbtFont.roboto(14, relativeTo: .subheadline))
        .foregroundColor(AndroidColor.textPrimary)
        .padding(.vertical, 4)
        .padding(.horizontal, 16)
        .accessibilityElement(children: .combine)
    }
}

/// The map of the result (Google lite mode on Android): the point with a marker and the accuracy circle in the
/// accent colour, at zoom 15, not interactive.
struct ResultMap: UIViewRepresentable {
    let point: GeoPoint

    private var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: point.lat, longitude: point.lon)
    }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.isUserInteractionEnabled = false
        map.showsCompass = false
        map.delegate = context.coordinator
        // Zoom 15 of Google Maps: about 4.8 m per point.
        map.setRegion(MKCoordinateRegion(center: coordinate, latitudinalMeters: 860, longitudinalMeters: 860), animated: false)
        let marker = MKPointAnnotation()
        marker.coordinate = coordinate
        map.addAnnotation(marker)
        if let accuracy = point.accuracy, accuracy.rounded() >= 1 {
            map.addOverlay(MKCircle(center: coordinate, radius: accuracy.rounded()))
        }
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            guard let circle = overlay as? MKCircle else { return MKOverlayRenderer(overlay: overlay) }
            let renderer = MKCircleRenderer(circle: circle)
            renderer.strokeColor = UIColor(RmbtColor.brand)
            renderer.lineWidth = 1.5
            renderer.fillColor = UIColor(RmbtColor.brand).withAlphaComponent(0x33 / 255)
            return renderer
        }
    }

    static func openInMaps(_ point: GeoPoint) {
        let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lon)
        let item = MKMapItem(placemark: MKPlacemark(coordinate: coordinate))
        item.name = L10n.tr("result.location")
        item.openInMaps(launchOptions: [MKLaunchOptionsMapCenterKey: NSValue(mkCoordinate: coordinate)])
    }
}

/// `setHistorySignalIcon`: the network icon of a history entry and of the result. iOS doesn't tell apps the
/// signal level, so Wi-Fi shows full strength; mobile networks show the "unknown signal" icon, as on Android.
struct HistorySignalIcon: View {
    let network: NetworkKind

    var body: some View {
        Group {
            switch network {
            case .wifi: RmbtImage.named("history_wifi").resizable().frame(width: 18, height: 12.77)
            case .ethernet: RmbtImage.named("history_ethernet").resizable().frame(width: 24, height: 24)
            case .cellular, .other: RmbtImage.named("history_signal_unknown").resizable().frame(width: 18, height: 11.14)
            }
        }
        .frame(width: 24, height: 24)
        .accessibilityHidden(true)
    }
}

extension TestRecord {
    /// `networkKindLabel`: "WLAN"/"Wi-Fi", "Mobile network", "Ethernet", "Network".
    var networkKindLabel: String {
        switch network {
        case .wifi: return L10n.tr("home.wifi")
        case .cellular: return L10n.tr("network.mobile")
        case .ethernet: return L10n.tr("network.ethernet")
        case .other: return L10n.tr("network.other")
        }
    }

    /// The network's own name; iOS gives apps no Wi-Fi or operator name, only the mobile generation.
    var networkDisplayName: String? {
        networkName ?? (network == .cellular ? technology : nil)
    }
}

extension GeoPoint: Identifiable {
    var id: String { "\(lat),\(lon)" }
}

#if DEBUG
struct ResultScreen_Previews: PreviewProvider {
    static func record(download: Double, location: GeoPoint?) -> TestRecord {
        TestRecord(
            testUuid: "1", openTestUuid: nil, time: Int64(Date().timeIntervalSince1970 * 1000), timezone: "Asia/Dushanbe",
            download: download, upload: 12.1, ping: 18, server: "Tojiktelecom (Dushanbe)", network: .cellular,
            networkName: nil, signalLevel: nil, location: location, technology: "4G"
        )
    }

    static var previews: some View {
        Group {
            NavigationStack { ResultScreen(record: record(download: 148.3, location: GeoPoint(lat: 38.5598, lon: 68.787, accuracy: 35))) }
                .previewDisplayName("Good, map")
            NavigationStack { ResultScreen(record: record(download: 3.2, location: nil)) }
                .previewDisplayName("Poor, no location")
            NavigationStack { ResultScreen(record: record(download: 48.3, location: nil)) }
                .environment(\.dynamicTypeSize, .accessibility3)
                .previewDisplayName("Large text")
        }
    }
}
#endif
