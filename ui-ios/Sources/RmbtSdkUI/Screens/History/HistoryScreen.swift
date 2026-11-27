import SwiftUI

/// The tests made on this device, newest first, one to one with the Android UI SDK (`rmbt_fragment_history.xml`,
/// `RmbtHistoryFragment`): the column header, then a row per test with the network icon, the time and the
/// overall rating, and the three values; a row opens the result. White, like the Android screen.
struct HistoryScreen: View {
    let records: [TestRecord]
    let onOpen: (String) -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(AndroidColor.divider)
                .frame(height: 1)
                .padding(.horizontal, 16)
            if records.isEmpty {
                HistoryHeader().hidden()
                HistoryEmpty()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                HistoryHeader()
                ScrollView {
                    HistoryRows(records: records, onOpen: onOpen)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .environment(\.colorScheme, .light)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.white, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.light, for: .navigationBar)
        .tint(AndroidColor.textGray)
        .toolbar {
            // `labelTitle`: on the left, after the back button when the history is pushed.
            ToolbarItem(placement: .navigationBarLeading) {
                Text(L10n.tr("history.title"))
                    .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
                    .foregroundColor(AndroidColor.textDarkGray)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }
}

/// The rows; they slide in one after another the first time the list is shown (`rmbt_layout_history_items`).
struct HistoryRows: View {
    let records: [TestRecord]
    let onOpen: (String) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(Array(records.enumerated()), id: \.element.id) { index, record in
                Button { onOpen(record.testUuid) } label: {
                    HistoryRow(record: record)
                }
                .buttonStyle(RowHighlightStyle())
                .opacity(appeared || reduceMotion ? 1 : 0)
                .offset(y: appeared || reduceMotion ? 0 : 16)
                .animation(.easeOut(duration: 0.3).delay(Double(min(index, 12)) * 0.024), value: appeared)
            }
        }
        .padding(.leading, 16)
        .onAppear { appeared = true }
    }
}

/// `rmbt_item_history_header`: "Date" and the three values with their units.
private struct HistoryHeader: View {
    var body: some View {
        WeightedHStack(weights: [2, 1, 1, 1]) {
            Text(L10n.tr("history.header.date"))
                .font(RmbtFont.roboto(12, relativeTo: .caption))
                .foregroundColor(AndroidColor.textGray60)
                .padding(.leading, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            column(L10n.tr("history.header.down"), L10n.tr("unit.speed"))
            column(L10n.tr("history.header.up"), L10n.tr("unit.speed")).padding(.leading, 4)
            column(L10n.tr("history.header.ping"), L10n.tr("unit.ping")).padding(.leading, 4)
        }
        .padding(.leading, 24)
        .padding(.trailing, 16)
        .padding(.vertical, 8)
        .accessibilityHidden(true)
    }

    private func column(_ name: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(name)
                .font(RmbtFont.roboto(12, relativeTo: .caption))
                .lineLimit(1)
                .minimumScaleFactor(10.0 / 12)
            Text(unit)
                .font(RmbtFont.roboto(10, relativeTo: .caption2))
        }
        .foregroundColor(AndroidColor.textGray60)
        .padding(.leading, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// `rmbt_item_history`: one test.
private struct HistoryRow: View {
    let record: TestRecord

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(WeightedHStack(weights: [2, 3]))
        layout {
            HStack(alignment: .center, spacing: 8) {
                HistorySignalIcon(network: record.network)
                VStack(alignment: .leading, spacing: 0) {
                    Text(HistoryTimeFormat.string(for: record.date, in: record.timeZone, separator: ", "))
                        .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                        .foregroundColor(AndroidColor.textDarkGray)
                    if let rating = record.overallRating {
                        Text(rating.name)
                            .font(RmbtFont.roboto(12, .medium, relativeTo: .caption))
                            .foregroundColor(rating.androidColor)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 0) {
                value("small_download-\(record.downloadRating?.rawValue ?? 0)", ValueFormat.speed(mbps: record.download))
                value("small_upload-\(record.uploadRating?.rawValue ?? 0)", ValueFormat.speed(mbps: record.upload))
                value("small_ping-\(record.pingRating?.rawValue ?? 0)", ValueFormat.ping(ms: record.ping))
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.vertical, 16)
        .frame(minHeight: 48)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
    }

    private func value(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 2) {
            RmbtImage.named(icon).resizable().frame(width: 16, height: 16)
            Text(text)
                .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                .foregroundColor(AndroidColor.textDarkGray)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var accessibilityText: String {
        [
            HistoryTimeFormat.string(for: record.date, in: record.timeZone, separator: ", "),
            record.overallRating?.name,
            "\(L10n.tr("metric.download")) \(ValueFormat.speed(mbps: record.download)) \(L10n.tr("unit.speed"))",
            "\(L10n.tr("metric.upload")) \(ValueFormat.speed(mbps: record.upload)) \(L10n.tr("unit.speed"))",
            "\(L10n.tr("metric.ping")) \(ValueFormat.ping(ms: record.ping)) \(L10n.tr("unit.ping"))",
        ].compactMap { $0 }.joined(separator: ", ")
    }
}

/// `layoutHistoryEmpty`: the history icon, "No data available." and where the tests come from.
private struct HistoryEmpty: View {
    var body: some View {
        VStack(spacing: 0) {
            RmbtImage.named("history_empty")
                .resizable()
                .frame(width: 64, height: 64)
                .opacity(0.4)
                .accessibilityHidden(true)
            Text(L10n.tr("history.no_data"))
                .font(RmbtFont.roboto(16, .medium, relativeTo: .callout))
                .foregroundColor(AndroidColor.textDarkGray)
                .padding(.top, 8)
            Text(L10n.tr("history.no_data.hint"))
                .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                .foregroundColor(AndroidColor.textGray60)
                .padding(.top, 2)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 16)
        .accessibilityElement(children: .combine)
    }
}

/// Android's `layout_weight`: the width shared out by weight, the children centred vertically.
struct WeightedHStack: Layout {
    let weights: [CGFloat]

    private func widths(_ total: CGFloat, count: Int) -> [CGFloat] {
        let used = Array(weights.prefix(count)) + Array(repeating: 1, count: max(0, count - weights.count))
        let sum = used.reduce(0, +)
        return used.map { total * $0 / sum }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? subviews.reduce(0) { $0 + $1.sizeThatFits(.unspecified).width }
        let height = zip(subviews, widths(width, count: subviews.count))
            .map { $0.sizeThatFits(ProposedViewSize(width: $1, height: nil)).height }
            .max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        for (subview, width) in zip(subviews, widths(bounds.width, count: subviews.count)) {
            subview.place(at: CGPoint(x: x, y: bounds.midY), anchor: .leading,
                          proposal: ProposedViewSize(width: width, height: bounds.height))
            x += width
        }
    }
}

/// `selectableItemBackground`: a light grey highlight while a row is pressed.
private struct RowHighlightStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Color.black.opacity(configuration.isPressed ? 0.06 : 0))
    }
}

/// The test history with its navigation; a row opens the result. Brings its own `NavigationStack`; inside an
/// app's own stack use ``RmbtHistoryListView`` instead.
public struct RmbtHistoryView: View {
    @State private var path: [String] = []

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            RmbtHistoryListView(onOpenResult: { path.append($0) })
                .navigationDestination(for: String.self) { RmbtResultView(testUuid: $0) }
        }
    }
}

/// The test history for an app that owns the navigation: push it in the app's `NavigationStack` and push
/// ``RmbtResultView`` for the test `onOpenResult` names. Its title sits in the navigation bar, on the left.
public struct RmbtHistoryListView: View {
    private let onOpenResult: (String) -> Void
    @ObservedObject private var history = HistoryStore.shared

    public init(onOpenResult: @escaping (String) -> Void) {
        self.onOpenResult = onOpenResult
    }

    public var body: some View {
        HistoryScreen(records: history.records, onOpen: onOpenResult)
    }
}

/// The start screen and the history in a tab bar, like the app.
public struct RmbtSpeedtestView: View {
    private let onClose: (() -> Void)?

    /// - Parameter onClose: shows a close button on the start screen that calls it, e.g. when presented modally.
    public init(onClose: (() -> Void)? = nil) {
        self.onClose = onClose
    }

    public var body: some View {
        TabView {
            RmbtHomeView(onClose: onClose)
                .tabItem { Label(L10n.tr("tab.home"), systemImage: "speedometer") }
            RmbtHistoryView()
                .tabItem { Label(L10n.tr("tab.history"), systemImage: "clock.arrow.circlepath") }
        }
        .tint(RmbtColor.brand)
    }
}

#if DEBUG
struct HistoryScreen_Previews: PreviewProvider {
    static let now = Int64(Date().timeIntervalSince1970 * 1000)
    static let records = [
        TestRecord(testUuid: "1", openTestUuid: nil, time: now, timezone: "Asia/Dushanbe", download: 148.3, upload: 52.1,
                   ping: 8, server: "", network: .wifi, networkName: nil, signalLevel: nil, location: nil, technology: nil),
        TestRecord(testUuid: "2", openTestUuid: nil, time: now - 86_400_000, timezone: "Asia/Dushanbe", download: 8.4, upload: 3.1,
                   ping: 48, server: "", network: .cellular, networkName: nil, signalLevel: nil, location: nil, technology: "4G"),
    ]

    static var previews: some View {
        Group {
            NavigationStack { HistoryScreen(records: records, onOpen: { _ in }) }.previewDisplayName("Entries")
            NavigationStack { HistoryScreen(records: [], onOpen: { _ in }) }.previewDisplayName("Empty")
            NavigationStack { HistoryScreen(records: records, onOpen: { _ in }) }
                .environment(\.dynamicTypeSize, .accessibility2)
                .previewDisplayName("Large text")
        }
    }
}
#endif
