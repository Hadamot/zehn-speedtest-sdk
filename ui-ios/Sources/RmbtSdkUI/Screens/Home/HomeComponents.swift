import SwiftUI

// The start screen's parts, ported one to one from the Android UI SDK (`rmbt_fragment_home.xml`,
// `RmbtHomeFragment`, `WaveView`, `LastTestCardView`); sizes in dp there are points here.

// MARK: - Colours

extension RmbtColor {
    /// `rmbt_bg_splash`: #0072EF at the top and in the middle, #004787 at the bottom.
    static let homeGradient = LinearGradient(
        stops: [
            .init(color: Color(red: 0x00 / 255, green: 0x72 / 255, blue: 0xEF / 255), location: 0),
            .init(color: Color(red: 0x00 / 255, green: 0x72 / 255, blue: 0xEF / 255), location: 0.5),
            .init(color: Color(red: 0x00 / 255, green: 0x47 / 255, blue: 0x87 / 255), location: 1),
        ],
        startPoint: .top,
        endPoint: .bottom
    )
    /// `rmbt_toolbar_gray`, the background without a network.
    static let homeOffline = Color(red: 0xF2 / 255, green: 0xF3 / 255, blue: 0xF5 / 255)
}

extension Rating {
    /// `ResultExplanations.color` of the Android UI SDK: the rating colour on the start, result and history screens.
    var androidColor: Color {
        switch self {
        case .excellent: return Color(red: 0x0D / 255, green: 0x5D / 255, blue: 0x9B / 255)
        case .good: return Color(red: 0x2E / 255, green: 0x9B / 255, blue: 0xEE / 255)
        case .normal: return Color(red: 0xFF / 255, green: 0xBD / 255, blue: 0x0C / 255)
        case .bad: return Color(red: 0xCF / 255, green: 0x0C / 255, blue: 0x0C / 255)
        }
    }
}

// MARK: - Wave

/// `WaveView`: two wave sprites twice as wide as the view slide left (3 s and 2.5 s per period), the
/// second one faint; without a network a flat "calm" line instead.
struct WaveBand: View {
    let isOn: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            if isOn {
                TimelineView(.animation(paused: reduceMotion)) { context in
                    let time = context.date.timeIntervalSinceReferenceDate
                    ZStack(alignment: .topLeading) {
                        WaveSprite(isOdd: true)
                            .fill(Color.white)
                            .frame(width: width * 2)
                            .offset(x: reduceMotion ? 0 : -width * Self.phase(time, period: 3.0))
                        WaveSprite(isOdd: false)
                            .fill(Color.white.opacity(0x33 / 255))
                            .frame(width: width * 2)
                            .offset(x: reduceMotion ? 0 : -width * Self.phase(time, period: 2.5))
                    }
                    .frame(width: width, alignment: .leading)
                }
            } else {
                CalmSprite().fill(Color.white)
            }
        }
        .clipped()
        .accessibilityHidden(true)
    }

    private static func phase(_ time: TimeInterval, period: TimeInterval) -> CGFloat {
        CGFloat(time.truncatingRemainder(dividingBy: period) / period)
    }
}

/// `WaveSpriteView.horizontalPath`: quadratic waves around the middle line, a fifth of the height from the
/// edges; the odd wave starts like a sine, the even one like a cosine.
private struct WaveSprite: Shape {
    let isOdd: Bool

    func path(in rect: CGRect) -> Path {
        let width = rect.width, height = rect.height, mid = height / 2
        let offset = height / 5
        let count = 4
        let length = width / CGFloat(count)
        var path = Path()
        path.move(to: CGPoint(x: 0, y: mid))
        var x: CGFloat = 0
        for i in (isOdd ? 1 : 0)...(isOdd ? count + 1 : count) {
            let control = CGPoint(x: x + length / 2, y: i % 2 == 0 ? offset : height - offset)
            path.addQuadCurve(to: CGPoint(x: x + length, y: mid), control: control)
            x += length
        }
        path.addLine(to: CGPoint(x: width, y: height))
        path.addLine(to: CGPoint(x: 0, y: height))
        path.closeSubpath()
        return path
    }
}

/// `CalmSpriteView`: the lower half filled.
private struct CalmSprite: Shape {
    func path(in rect: CGRect) -> Path {
        Path(CGRect(x: rect.minX, y: rect.midY, width: rect.width, height: rect.height / 2))
    }
}

// MARK: - Start button

/// The start circle (`ivIsConnectedCircle`) with the network icon on it (`ivSignalLevel`), the Ethernet label
/// above the icon (`ivTechnology`) and the light rings spreading from it (`vStartHalo1/2`). While a test can
/// be started the circle "breathes"; without a network the circle is grey and the icon crossed out.
struct StartCircle: View {
    let network: NetworkState
    /// Height of the screen the Android layout's percentages refer to.
    let screenHeight: CGFloat
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var invitationStart = Date()

    var body: some View {
        let side = screenHeight * 0.35
        let iconBox = screenHeight * 0.16
        TimelineView(.animation(paused: reduceMotion || !network.isConnected)) { context in
            let elapsed = context.date.timeIntervalSince(invitationStart)
            ZStack {
                if network.isConnected && !reduceMotion {
                    halo(elapsed: elapsed, side: side)
                    halo(elapsed: elapsed - 1, side: side)
                }
                RmbtImage.named(network.isConnected ? "home_with_internet_circle" : "home_without_internet_circle")
                    .resizable()
                    .scaledToFit()
                    .frame(width: side, height: side)
                    .scaleEffect(network.isConnected && !reduceMotion ? Self.pulse(elapsed) : 1)
                    .accessibilityHidden(true)
                Button(action: action) {
                    ZStack(alignment: .top) {
                        icon
                            .frame(maxWidth: iconBox, maxHeight: iconBox)
                            .frame(width: iconBox, height: iconBox)
                        if network.isConnected && network.kind == .ethernet {
                            RmbtImage.named("home_label_ethernet")
                                .resizable()
                                .scaledToFit()
                                .frame(height: min(10, screenHeight * 0.035))
                        }
                    }
                    .frame(width: side * 0.77, height: side * 0.77)
                    .contentShape(Circle())
                }
                .buttonStyle(PressScaleButtonStyle())
                .accessibilityLabel(L10n.tr("home.start.a11y"))
            }
            .frame(maxWidth: .infinity)
            .frame(height: side)
        }
        .onChange(of: network.isConnected) { _ in invitationStart = Date() }
    }

    /// `setSignalIcon`: the network icon in the accent colour, at its own size unless the box is smaller.
    /// iOS doesn't tell apps the signal level, so the icons show full strength.
    @ViewBuilder
    private var icon: some View {
        if !network.isConnected {
            fitted("home_no_internet", width: 84, height: 70)
        } else {
            switch network.kind {
            case .wifi: fitted("home_wifi", width: 114, height: 80.874).foregroundColor(RmbtColor.brand)
            case .cellular: fitted("home_mobile", width: 84, height: 52).foregroundColor(RmbtColor.brand)
            case .ethernet: fitted("home_ethernet", width: 64, height: 64).foregroundColor(RmbtColor.brand)
            case .other, nil: fitted("home_signal_unknown", width: 84, height: 52).foregroundColor(RmbtColor.brand)
            }
        }
    }

    /// Android's `centerInside`: the intrinsic size, scaled down only to fit.
    private func fitted(_ name: String, width: CGFloat, height: CGFloat) -> some View {
        RmbtImage.named(name)
            .resizable()
            .scaledToFit()
            .frame(maxWidth: width, maxHeight: height)
    }

    /// `UiFeedback.startBeacon(from: 0.78, to: 1.05)`: a white disc grows from the circle's edge while fading
    /// from 35 % to nothing, every 2 s, decelerating.
    @ViewBuilder
    private func halo(elapsed: TimeInterval, side: CGFloat) -> some View {
        if elapsed >= 0 {
            let t = elapsed.truncatingRemainder(dividingBy: 2) / 2
            let eased = 1 - (1 - t) * (1 - t)
            Circle()
                .fill(Color.white)
                .frame(width: side, height: side)
                .scaleEffect(0.78 + (1.05 - 0.78) * eased)
                .opacity(0.35 * (1 - eased))
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }

    /// `UiFeedback.startPulse(scale: 1.04)`: 1 → 1.04 → 1, 1.1 s each way, accelerating and decelerating.
    private static func pulse(_ elapsed: TimeInterval) -> CGFloat {
        let t = elapsed.truncatingRemainder(dividingBy: 2.2) / 1.1
        let triangle = t <= 1 ? t : 2 - t
        return 1 + 0.04 * (0.5 - 0.5 * cos(.pi * triangle))
    }
}

/// `tv_info`: "Press the button to start the test now!" in a white bubble pointing at the circle.
struct StartHintBubble: View {
    let onDismiss: () -> Void

    var body: some View {
        Button(action: onDismiss) {
            Text(L10n.tr("home.start.info"))
                .font(RmbtFont.roboto(16, relativeTo: .callout))
                .foregroundColor(Color.black.opacity(0.87))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 16)
                .frame(minHeight: 48)
                .background(
                    BubbleShape()
                        .fill(Color.white)
                        .shadow(color: .black.opacity(0.18), radius: 2, y: 1)
                )
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 24)
        .transition(.opacity)
    }
}

/// A rounded box with a small pointer at the bottom middle (`rmbt_bg_info_window.9.png`).
private struct BubbleShape: Shape {
    func path(in rect: CGRect) -> Path {
        let pointer: CGFloat = 8
        let box = CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height - pointer)
        var path = Path(roundedRect: box, cornerRadius: 4)
        path.move(to: CGPoint(x: rect.midX - pointer, y: box.maxY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.midX + pointer, y: box.maxY))
        path.closeSubpath()
        return path
    }
}

/// Shrinks a button slightly while it is pressed (`UiFeedback.pressScale`: 0.94, overshooting back).
struct PressScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(configuration.isPressed ? .easeOut(duration: 0.12) : .spring(response: 0.3, dampingFraction: 0.5),
                       value: configuration.isPressed)
    }
}

// MARK: - Last test card

/// `LastTestCardView`: when and how fast the newest test was; opens its result. Without tests it says where
/// results will appear. Time and values share a row; at large text sizes the time goes above the values.
struct LastTestCardView: View {
    let record: TestRecord?
    let onOpen: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Group {
            if let record {
                Button(action: onOpen) { content(record) }
                    .buttonStyle(PressScaleButtonStyle())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(accessibilityText(record))
                    .accessibilityAddTraits(.isButton)
            } else {
                Text(L10n.tr("home.last_test.empty"))
                    .font(RmbtFont.roboto(14, relativeTo: .subheadline))
                    .foregroundColor(AndroidColor.textGray)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .frame(maxWidth: .infinity, minHeight: 64)
                    .background(card)
            }
        }
        // The bottom of the start screen is white in both appearances, so the card keeps light colours.
        .environment(\.colorScheme, .light)
    }

    private var card: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous).fill(RmbtColor.lastTestCard)
    }

    private func content(_ record: TestRecord) -> some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(alignment: .center, spacing: 12))
        return HStack(spacing: 4) {
            layout {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr("home.last_test.title"))
                        .font(RmbtFont.roboto(12, relativeTo: .caption))
                        .foregroundColor(AndroidColor.textGray)
                    Text(HistoryTimeFormat.string(for: record.date, in: record.timeZone, separator: ", "))
                        .font(RmbtFont.roboto(14, .medium, relativeTo: .subheadline))
                        .foregroundColor(AndroidColor.textDarkGray)
                        .lineLimit(2)
                }
                .fixedSize(horizontal: !dynamicTypeSize.isAccessibilitySize, vertical: false)
                HStack(alignment: .top, spacing: 4) {
                    metric(L10n.tr("metric.download"), "small_download-\(record.downloadRating?.rawValue ?? 0)",
                           ValueFormat.speed(mbps: record.download), L10n.tr("unit.speed"))
                    metric(L10n.tr("metric.upload"), "small_upload-\(record.uploadRating?.rawValue ?? 0)",
                           ValueFormat.speed(mbps: record.upload), L10n.tr("unit.speed"))
                    metric(L10n.tr("metric.ping"), "small_ping-\(record.pingRating?.rawValue ?? 0)",
                           ValueFormat.ping(ms: record.ping), L10n.tr("unit.ping"))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            RmbtImage.named("card_chevron")
                .resizable()
                .frame(width: 16, height: 16)
        }
        // 8 pt margin, the 3 pt stripe and 7 pt padding, as in the Android layout.
        .padding(.leading, 18)
        .padding([.vertical, .trailing], 12)
        .frame(maxWidth: .infinity, minHeight: 64)
        .background(card)
        .overlay(alignment: .leading) {
            if let rating = record.overallRating {
                Capsule()
                    .fill(rating.androidColor)
                    .frame(width: 3)
                    .padding(.vertical, 14)
                    .padding(.leading, 8)
            }
        }
    }

    private func metric(_ name: String, _ icon: String, _ value: String, _ unit: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(name)
                .font(RmbtFont.roboto(11, relativeTo: .caption2))
                .foregroundColor(AndroidColor.textGray)
                .lineLimit(1)
                .truncationMode(.tail)
            HStack(spacing: 3) {
                RmbtImage.named(icon).resizable().frame(width: 16, height: 16)
                Text(value)
                    .font(RmbtFont.roboto(16, .medium, relativeTo: .callout))
                    .foregroundColor(AndroidColor.textDarkGray)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Text(unit)
                .font(RmbtFont.roboto(11, relativeTo: .caption2))
                .foregroundColor(AndroidColor.textGray)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Read as one element by VoiceOver: "Last test, Today, 09:10, Download 45.2 Mbit/s, …, Good".
    private func accessibilityText(_ record: TestRecord) -> String {
        [
            L10n.tr("home.last_test.title"),
            HistoryTimeFormat.string(for: record.date, in: record.timeZone, separator: ", "),
            "\(L10n.tr("metric.download")) \(ValueFormat.speed(mbps: record.download)) \(L10n.tr("unit.speed"))",
            "\(L10n.tr("metric.upload")) \(ValueFormat.speed(mbps: record.upload)) \(L10n.tr("unit.speed"))",
            "\(L10n.tr("metric.ping")) \(ValueFormat.ping(ms: record.ping)) \(L10n.tr("unit.ping"))",
            record.overallRating?.name,
        ].compactMap { $0 }.joined(separator: ", ")
    }
}
