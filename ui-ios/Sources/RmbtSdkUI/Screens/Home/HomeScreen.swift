import SwiftUI
import UIKit

/// The start screen, one to one with the Android UI SDK (`rmbt_fragment_home.xml`, `RmbtHomeFragment`), drawn
/// from its state: the start circle on the blue gradient while a network is available (grey without one), the
/// network under it, the wave and the last test at the bottom. Sizes given in percent there are percent of
/// the screen height here; the navigation bar above holds the logo, as the Android logo row does.
struct HomeScreen: View {
    let network: NetworkState
    let lastTest: TestRecord?
    let onStart: () -> Void
    let onOpenLastTest: () -> Void

    @State private var isHintShown = false
    @State private var isHintDismissed = false
    @State private var isOfflineAlertShown = false

    /// The Android logo row: 8 pt margin and the 36 pt logo, here the navigation bar.
    private static let logoRowHeight: CGFloat = 44

    var body: some View {
        GeometryReader { geo in
            let screenHeight = geo.size.height + Self.logoRowHeight
            VStack(spacing: 0) {
                StartCircle(network: network, screenHeight: screenHeight, action: tapStart)
                    .padding(.top, 16)
                    .overlay(alignment: .top) {
                        if isHintShown {
                            StartHintBubble { dismissHint() }
                                .alignmentGuide(.top) { $0[.bottom] - screenHeight * 0.037 - 16 }
                        }
                    }
                networkLabels
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                WaveBand(isOn: network.isConnected)
                    .frame(height: screenHeight * 0.12)
                LastTestCardView(record: lastTest, onOpen: onOpenLastTest)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                    .background(Color.white.ignoresSafeArea(edges: .bottom))
            }
        }
        .background(background.ignoresSafeArea())
        .task(id: network.isConnected) { await showHintLater() }
        .alert(L10n.tr("home.no_internet"), isPresented: $isOfflineAlertShown) {
            Button(L10n.tr("common.ok"), role: .cancel) {}
        }
    }

    @ViewBuilder
    private var background: some View {
        if network.isConnected {
            RmbtColor.homeGradient
        } else {
            RmbtColor.homeOffline
        }
    }

    /// `tvNetworkType`, `tvNetworkName` and `tvLblNoConnection`, centred between the circle and the wave. The
    /// small line above the name is the kind of network when the name is the network's own; iOS gives apps no
    /// Wi-Fi or operator name, so the name is the kind ("Wi-Fi", "Mobile network").
    private var networkLabels: some View {
        VStack(spacing: 0) {
            Text(network.isConnected ? kindLabel : L10n.tr("home.no_internet"))
                .font(RmbtFont.roboto(20, .medium, relativeTo: .title3))
                .foregroundColor(network.isConnected ? .white : AndroidColor.textDarkGray)
            if !network.isConnected {
                Text(L10n.tr("home.no_internet.details"))
                    .font(RmbtFont.roboto(16, relativeTo: .callout))
                    .foregroundColor(AndroidColor.textGray)
            }
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 16)
        .accessibilityElement(children: .combine)
    }

    /// `networkKindLabel`.
    private var kindLabel: String {
        switch network.kind {
        case .wifi: return L10n.tr("home.wifi")
        case .cellular: return L10n.tr("network.mobile")
        case .ethernet: return L10n.tr("network.ethernet")
        case .other, nil: return L10n.tr("network.other")
        }
    }

    /// Without a network the button explains why no test is possible.
    private func tapStart() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if network.isConnected {
            onStart()
        } else {
            isOfflineAlertShown = true
        }
    }

    /// The hint "tap to start" appears when the user has not started a test for 2 s; a tap hides it for good.
    private func showHintLater() async {
        guard network.isConnected else {
            isHintShown = false
            return
        }
        guard !isHintDismissed else { return }
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        guard !Task.isCancelled, network.isConnected, !isHintDismissed else { return }
        withAnimation(.easeOut(duration: 0.2)) { isHintShown = true }
    }

    private func dismissHint() {
        isHintDismissed = true
        withAnimation(.easeIn(duration: 0.15)) { isHintShown = false }
    }
}

/// The start screen with its navigation: "Start" opens the running test full screen, a finished test and the
/// last-test card open the result. Brings its own `NavigationStack`; inside an app's own stack use
/// ``RmbtStartView`` instead.
public struct RmbtHomeView: View {
    private let onClose: (() -> Void)?
    @State private var path: [String] = []

    /// - Parameter onClose: shows a close button that calls it, e.g. when the screen is presented modally.
    public init(onClose: (() -> Void)? = nil) {
        self.onClose = onClose
    }

    public var body: some View {
        NavigationStack(path: $path) {
            RmbtStartView(onOpenResult: { path.append($0) }, onClose: onClose)
                .navigationDestination(for: String.self) { RmbtResultView(testUuid: $0) }
        }
    }
}

/// The start screen for an app that owns the navigation: put it at the root of the app's `NavigationStack`
/// (without hiding the navigation bar, which carries the logo) and push ``RmbtResultView`` for the test
/// `onOpenResult` names, after a finished test or a tap on the last-test card. The running test opens full
/// screen by itself.
public struct RmbtStartView: View {
    private let onOpenResult: (String) -> Void
    private let onClose: (() -> Void)?

    @ObservedObject private var history = HistoryStore.shared
    @ObservedObject private var network = NetworkMonitor.shared
    @State private var test: TestLaunch?
    @State private var location = LocationSource()

    /// - Parameters:
    ///   - onOpenResult: the test UUID whose ``RmbtResultView`` to show.
    ///   - onClose: shows a close button that calls it, e.g. when the screen is presented modally.
    public init(onOpenResult: @escaping (String) -> Void, onClose: (() -> Void)? = nil) {
        self.onOpenResult = onOpenResult
        self.onClose = onClose
    }

    public var body: some View {
        HomeScreen(
            network: network.state,
            lastTest: history.records.first,
            onStart: start,
            onOpenLastTest: { history.records.first.map { onOpenResult($0.testUuid) } }
        )
        .toolbar {
            ToolbarItem(placement: .principal) {
                RmbtImage.named(network.state.isConnected ? "brand_logo_white" : "brand_logo")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 36)
                    .accessibilityHidden(true)
            }
            if let onClose {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onClose) { Image(systemName: "xmark") }
                        .tint(network.state.isConnected ? .white : AndroidColor.textDarkGray)
                        .accessibilityLabel(L10n.tr("test.close"))
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(network.state.isConnected ? .dark : .light, for: .navigationBar)
        .fullScreenCover(item: $test) { launch in
            MeasurementScreen(
                viewModel: MeasurementViewModel(
                    network: launch.network,
                    engine: KmpSpeedTestEngine(),
                    store: HistoryStore.shared,
                    locate: { [location] in await location.current() }
                ),
                onFinished: { record in
                    test = nil
                    onOpenResult(record.testUuid)
                },
                onClose: { test = nil }
            )
        }
        .onAppear { network.start() }
    }

    private func start() {
        location.requestPermissionIfNeeded()
        test = TestLaunch(network: network.state)
    }
}

/// One start of the test screen.
private struct TestLaunch: Identifiable {
    let id = UUID()
    let network: NetworkState
}

#if DEBUG
struct HomeScreen_Previews: PreviewProvider {
    static let record = TestRecord(
        testUuid: "1", openTestUuid: nil, time: Int64(Date().timeIntervalSince1970 * 1000), timezone: "Asia/Dushanbe",
        download: 48.3, upload: 12.1, ping: 18, server: "Dushanbe", network: .cellular, networkName: nil,
        signalLevel: nil, location: nil, technology: "4G"
    )

    static var previews: some View {
        Group {
            HomeScreen(network: NetworkState(isConnected: true, kind: .wifi), lastTest: record, onStart: {}, onOpenLastTest: {})
                .previewDisplayName("Wi-Fi, last test")
            HomeScreen(network: NetworkState(isConnected: true, kind: .cellular, technology: "4G"), lastTest: nil,
                       onStart: {}, onOpenLastTest: {})
                .previewDisplayName("4G, no tests")
            HomeScreen(network: .offline, lastTest: record, onStart: {}, onOpenLastTest: {})
                .previewDisplayName("Offline")
            HomeScreen(network: NetworkState(isConnected: true, kind: .wifi), lastTest: record, onStart: {}, onOpenLastTest: {})
                .environment(\.dynamicTypeSize, .accessibility2)
                .previewDisplayName("Large text")
        }
    }
}
#endif
