import SwiftUI
import UIKit

/// The running test (open-rmbt-ios `RMBTTestViewController` + `RMBTTestPortraitView`): network and technology,
/// the progress and speed gauges, the values and the current step. Starts the test when it appears; leaving
/// the screen cancels it.
struct MeasurementScreen: View {
    @StateObject private var viewModel: MeasurementViewModel
    let onFinished: (TestRecord) -> Void
    let onClose: () -> Void

    @State private var attempt = 0
    @State private var isConfirmingAbort = false

    init(viewModel: @autoclosure @escaping () -> MeasurementViewModel,
         onFinished: @escaping (TestRecord) -> Void,
         onClose: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: viewModel())
        self.onFinished = onFinished
        self.onClose = onClose
    }

    var body: some View {
        MeasurementContent(
            state: viewModel.state,
            network: viewModel.network,
            onClose: { isConfirmingAbort = true }
        )
        .task(id: attempt) { await viewModel.run() }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .onChange(of: viewModel.state.outcome) { outcome in
            guard case .finished(let record) = outcome else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            onFinished(record)
        }
        .onChange(of: viewModel.state.step) { step in
            // VoiceOver users can't see the gauges move: say when the test moves on to the next step.
            UIAccessibility.post(notification: .announcement, argument: step.title)
        }
        .alert(L10n.appName, isPresented: $isConfirmingAbort) {
            Button(L10n.tr("test.abort.confirm"), role: .destructive, action: onClose)
            Button(L10n.tr("test.abort.continue"), role: .cancel) {}
        } message: {
            Text(L10n.tr("test.abort.message"))
        }
        .alert(L10n.tr("test.error.title"), isPresented: failedBinding) {
            Button(L10n.tr("common.cancel"), role: .cancel, action: onClose)
            Button(L10n.tr("test.error.retry")) { attempt += 1 }
        } message: {
            Text(L10n.tr("test.error.message"))
        }
    }

    private var failedBinding: Binding<Bool> {
        Binding(get: { viewModel.state.outcome == .failed }, set: { _ in })
    }
}

/// The test screen drawn from its state only, for previews.
struct MeasurementContent: View {
    let state: MeasurementUiState
    let network: NetworkState
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            GeometryReader { geo in
                gauges(in: geo.size)
                    .frame(width: geo.size.width, height: geo.size.height)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            panel
        }
        .background(RmbtColor.testBackground.ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    // MARK: Network

    private var header: some View {
        HStack(alignment: .top, spacing: 16) {
            labeled(L10n.tr("test.network"), networkName)
                .frame(maxWidth: .infinity, alignment: .leading)
            labeled(L10n.tr("test.technology"), network.technology ?? ValueFormat.dash)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 20))
                    .foregroundColor(.white.opacity(0.85))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(L10n.tr("test.close"))
            .padding(.top, -10)
        }
        .padding(.leading, 20)
        .padding(.trailing, 10)
        .padding(.top, 16)
    }

    private var networkName: String {
        switch network.kind {
        case .wifi: return L10n.tr("network.wifi")
        case .cellular: return L10n.tr("network.mobile")
        case .ethernet: return L10n.tr("network.ethernet")
        case .other, nil: return L10n.tr("network.other")
        }
    }

    private func labeled(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(RmbtFont.roboto(14, .medium, relativeTo: .subheadline))
                .foregroundColor(.white.opacity(0.65))
            Text(value)
                .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Gauges

    /// As in the app's layout (374 pt wide): the progress gauge at the top right, the speed gauge below it at
    /// the left, overlapping by a fifth of their size.
    private func gauges(in size: CGSize) -> some View {
        let overlap = 50 / 230.5
        let side = min(size.width * 230.5 / 374, size.height / (2 - overlap))
        let scale = side / 230.5
        return ZStack(alignment: .topLeading) {
            ZStack {
                GaugeView.progress(state.progressGauge)
                GaugeStageLabels()
                progressValue(scale: scale)
            }
            .frame(width: side, height: side)
            .offset(x: size.width - side)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L10n.tr("test.a11y.progress"))
            .accessibilityValue("\(state.totalPercent) %, \(state.step.title)")
            .accessibilityAddTraits(.updatesFrequently)

            ZStack {
                GaugeView.speed(state.speedGauge)
                speedValue(scale: scale)
            }
            .frame(width: side, height: side)
            .offset(y: side * (1 - overlap))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L10n.tr("test.a11y.speed"))
            .accessibilityValue(ValueFormat.withUnit(ValueFormat.liveSpeed(mbps: state.liveMbps), L10n.tr("unit.speed")))
            .accessibilityAddTraits(.updatesFrequently)
        }
        .frame(width: size.width, height: side * (2 - overlap), alignment: .topLeading)
        .animation(.linear(duration: 0.15), value: state.progressGauge)
        .animation(.linear(duration: 0.15), value: state.speedGauge)
    }

    private func progressValue(scale: CGFloat) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 1) {
            Text("\(state.totalPercent)")
                .font(RmbtFont.roboto(fixed: 64 * scale, .medium))
                .monospacedDigit()
            Text("%")
                .font(RmbtFont.roboto(fixed: 20 * scale, .medium))
        }
        .foregroundColor(.white)
    }

    private func speedValue(scale: CGFloat) -> some View {
        HStack(alignment: .center, spacing: 4 * scale) {
            Text(state.isTransferring && state.liveMbps > 0 ? ValueFormat.liveSpeed(mbps: state.liveMbps) : "")
                .font(RmbtFont.roboto(fixed: 64 * scale, .medium))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            VStack(spacing: 6 * scale) {
                if state.isTransferring {
                    RmbtImage.named(state.step == .download ? "download_icon" : "upload_icon")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22 * scale, height: 22 * scale)
                }
                if state.isTransferring && state.liveMbps > 0 {
                    Text(L10n.tr("unit.speed"))
                        .font(RmbtFont.roboto(fixed: 17 * scale, .medium))
                }
            }
        }
        .foregroundColor(.white)
        .frame(width: 150 * scale)
        .offset(x: 10 * scale)
    }

    // MARK: Values and step

    private var panel: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 16) {
                networkIcon
                    .frame(width: 20)
                    .opacity(0.3)
                metric(L10n.tr("metric.download"), RmbtImage.download(state.downloadMbps.flatMap { Rating.download(mbps: $0) }),
                       state.downloadMbps.map { ValueFormat.withUnit(ValueFormat.liveSpeed(mbps: $0), L10n.tr("unit.speed")) })
                metric(L10n.tr("metric.upload"), RmbtImage.upload(state.uploadMbps.flatMap { Rating.upload(mbps: $0) }),
                       state.uploadMbps.map { ValueFormat.withUnit(ValueFormat.liveSpeed(mbps: $0), L10n.tr("unit.speed")) })
                metric(L10n.tr("metric.ping"), RmbtImage.ping(state.pingMs.flatMap { Rating.ping(ms: $0) }),
                       state.pingMs.map { ValueFormat.withUnit(ValueFormat.ping(ms: $0), L10n.tr("unit.ping")) })
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)

            VStack(alignment: .leading, spacing: 2) {
                Text(state.step.title)
                    .font(RmbtFont.roboto(16, .medium, relativeTo: .callout))
                    .foregroundColor(.white)
                Text(state.step.hint)
                    .font(RmbtFont.roboto(12, .regular, relativeTo: .caption))
                    .foregroundColor(.white.opacity(0.6))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .accessibilityElement(children: .combine)

            SpeedChart(points: state.isTransferring ? state.chart : [])
                .frame(height: 88)
                .accessibilityHidden(true)
        }
        .background(RmbtColor.testBackground.shadow(color: .black.opacity(0.2), radius: 3, y: -1))
    }

    @ViewBuilder
    private var networkIcon: some View {
        switch network.kind {
        case .cellular:
            RmbtImage.named("mobile_icon_full").resizable().renderingMode(.template).scaledToFit()
                .foregroundColor(.white)
        default:
            RmbtImage.named("wifi_icon").resizable().renderingMode(.template).scaledToFit()
                .foregroundColor(.white)
        }
    }

    private func metric(_ title: String, _ icon: Image, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(RmbtFont.roboto(14, .regular, relativeTo: .subheadline))
                .foregroundColor(.white.opacity(0.65))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            HStack(spacing: 5) {
                icon.resizable().scaledToFit().frame(width: 16, height: 16)
                Text(value ?? ValueFormat.dash)
                    .font(RmbtFont.roboto(12, .medium, relativeTo: .caption))
                    .foregroundColor(.white)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Live speed of the running download or upload over its progress, on a log scale like the speed gauge.
struct SpeedChart: View {
    let points: [SpeedPoint]

    var body: some View {
        GeometryReader { geo in
            let line = path(in: geo.size)
            ZStack {
                line.fill.fill(LinearGradient(
                    colors: [RmbtColor.graphAccent.opacity(0.45), RmbtColor.graphAccent.opacity(0.05)],
                    startPoint: .top, endPoint: .bottom
                ))
                line.stroke.stroke(RmbtColor.graphAccent, style: StrokeStyle(lineWidth: 2, lineJoin: .round))
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    private func path(in size: CGSize) -> (stroke: Path, fill: Path) {
        guard let first = points.first else { return (Path(), Path()) }
        func point(_ p: SpeedPoint) -> CGPoint {
            CGPoint(x: size.width * p.fraction, y: size.height * (1 - SpeedGauge.value(mbps: p.mbps)))
        }
        var stroke = Path()
        stroke.move(to: point(first))
        points.dropFirst().forEach { stroke.addLine(to: point($0)) }
        var fill = stroke
        fill.addLine(to: CGPoint(x: size.width * (points.last?.fraction ?? 0), y: size.height))
        fill.addLine(to: CGPoint(x: size.width * first.fraction, y: size.height))
        fill.closeSubpath()
        return (stroke, fill)
    }
}

#if DEBUG
struct MeasurementContent_Previews: PreviewProvider {
    static var previews: some View {
        let download = MeasurementUiState(
            step: .download, stepFraction: 0.6, liveMbps: 48.3, downloadMbps: 48.3,
            chart: (0...12).map { SpeedPoint(fraction: Double($0) / 20, mbps: 30 + Double($0) * 2) }
        )
        Group {
            MeasurementContent(state: MeasurementUiState(), network: NetworkState(isConnected: true, kind: .wifi), onClose: {})
                .previewDisplayName("Connecting")
            MeasurementContent(state: download, network: NetworkState(isConnected: true, kind: .cellular, technology: "4G"), onClose: {})
                .previewDisplayName("Download")
            MeasurementContent(state: download, network: NetworkState(isConnected: true, kind: .wifi), onClose: {})
                .previewDevice("iPhone SE (3rd generation)")
                .environment(\.locale, Locale(identifier: "ru"))
                .previewDisplayName("SE, ru")
        }
    }
}
#endif
