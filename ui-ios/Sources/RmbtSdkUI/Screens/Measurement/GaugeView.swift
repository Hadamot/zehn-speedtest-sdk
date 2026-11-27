import SwiftUI

/// A gauge of the test screen (open-rmbt-ios `RMBTGaugeView`): the foreground image is revealed along an arc
/// from `startAngle` to `endAngle` (degrees, clockwise from 3 o'clock) as `value` goes from 0 to 1.
struct GaugeView: View {
    let background: String
    let foreground: String
    let startAngle: Double
    let endAngle: Double
    let value: Double

    var body: some View {
        ZStack {
            RmbtImage.named(background).resizable().scaledToFit()
            RmbtImage.named(foreground).resizable().scaledToFit()
                .mask(GaugeWedge(start: startAngle, end: startAngle + (endAngle - startAngle) * min(max(value, 0), 1)))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    static func progress(_ value: Double) -> GaugeView {
        GaugeView(background: "gauge_progress_bg-1", foreground: "gauge_progress_fg-1", startAngle: 215, endAngle: 495, value: value)
    }

    static func speed(_ value: Double) -> GaugeView {
        GaugeView(background: "gauge_speed_bg-1", foreground: "gauge_speed_fg-1", startAngle: 29, endAngle: 295, value: value)
    }
}

/// A pie slice from the centre, bigger than the view, so it covers everything between the two angles.
private struct GaugeWedge: Shape {
    var start: Double
    var end: Double

    var animatableData: Double {
        get { end }
        set { end = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        path.move(to: center)
        // SwiftUI's `clockwise: false` turns clockwise on screen (the y axis points down).
        path.addArc(center: center, radius: rect.width, startAngle: .degrees(start), endAngle: .degrees(end), clockwise: false)
        path.closeSubpath()
        return path
    }
}

/// Step names around the progress gauge, drawn as text so they are localized. Positions and angles are those
/// of the outlines they replace in the gauge image (viewBox 198.532 × 222.998).
struct GaugeStageLabels: View {
    private struct Stage {
        let text: String
        let center: CGPoint
        let angle: Double
        let maxWidth: CGFloat
    }

    private static let svgSize = CGSize(width: 198.532, height: 222.998)

    private var stages: [Stage] {
        [
            Stage(text: L10n.tr("test.gauge.init"), center: CGPoint(x: 27.0, y: 55.6), angle: -42, maxWidth: 24),
            Stage(text: L10n.tr("metric.ping"), center: CGPoint(x: 71.6, y: 31.6), angle: -12, maxWidth: 30),
            Stage(text: L10n.tr("metric.download"), center: CGPoint(x: 130.4, y: 43.7), angle: 31, maxWidth: 42),
            Stage(text: L10n.tr("metric.upload"), center: CGPoint(x: 169.0, y: 117.1), angle: 93, maxWidth: 40),
            Stage(text: L10n.tr("test.gauge.qos"), center: CGPoint(x: 121.5, y: 186.5), angle: -19, maxWidth: 26),
        ]
    }

    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width / Self.svgSize.width, geo.size.height / Self.svgSize.height)
            let origin = CGPoint(
                x: (geo.size.width - Self.svgSize.width * scale) / 2,
                y: (geo.size.height - Self.svgSize.height * scale) / 2
            )
            ForEach(stages, id: \.text) { stage in
                Text(stage.text)
                    .font(RmbtFont.roboto(fixed: 7.5 * scale, .medium))
                    .foregroundColor(.white.opacity(0.65))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .frame(width: stage.maxWidth * scale, height: 11 * scale)
                    .rotationEffect(.degrees(stage.angle))
                    .position(x: origin.x + stage.center.x * scale, y: origin.y + stage.center.y * scale)
            }
        }
        .accessibilityHidden(true)
    }
}
