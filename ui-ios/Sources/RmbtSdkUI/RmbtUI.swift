import RmbtSdk
import SwiftUI
import UIKit

/// Settings of the ready-made speed test screens.
public struct RmbtUiConfig {
    /// Control Server base URL; the Tojiktelecom backend by default.
    public var controlUrl: String
    /// Sent as `X-Api-Key`, identifies the host app at a gateway.
    public var apiKey: String?
    /// Free label stored with every test on the server.
    public var tag: String?
    /// Shown in the shared result ("<appName> speed test result"); the host app's display name by default.
    public var appName: String?
    /// Language of the screens ("en", "ru", "tg"), e.g. the one chosen inside the host app; the system's
    /// choice among the host app's languages by default.
    public var language: String?
    /// Asks for the location on "Start" when the host app has `NSLocationWhenInUseUsageDescription` in its
    /// Info.plist and the user hasn't decided yet; the test runs whatever the answer. With the permission the
    /// result shows where the test ran on a map. Turn off when the host app asks itself.
    public var requestLocationPermission: Bool

    public init(
        controlUrl: String = RmbtConfig.companion.DEFAULT_CONTROL_URL,
        apiKey: String? = nil,
        tag: String? = nil,
        appName: String? = nil,
        language: String? = nil,
        requestLocationPermission: Bool = true
    ) {
        self.controlUrl = controlUrl
        self.apiKey = apiKey
        self.tag = tag
        self.appName = appName
        self.language = language
        self.requestLocationPermission = requestLocationPermission
    }
}

/// Entry point of the speed test screens ported from open-rmbt-ios: start screen, running test, result and
/// history.
///
/// ```swift
/// RmbtUI.configure(RmbtUiConfig(apiKey: "my-app"))   // optional
/// RmbtUI.present(from: viewController)               // UIKit
/// RmbtSpeedtestView()                                // SwiftUI
/// ```
///
/// The tabs can also be embedded on their own: ``RmbtHomeView`` and ``RmbtHistoryView`` (each brings its own
/// `NavigationStack`, so don't put them inside another one).
public enum RmbtUI {

    public private(set) static var config = RmbtUiConfig()

    /// Call before showing a screen, e.g. at app start.
    public static func configure(_ config: RmbtUiConfig) {
        self.config = config
    }

    /// The start screen and the history in a tab bar, for presenting or pushing from UIKit. With `onClose` the
    /// start screen shows a close button.
    @MainActor
    public static func makeViewController(onClose: (() -> Void)? = nil) -> UIViewController {
        UIHostingController(rootView: RmbtSpeedtestView(onClose: onClose))
    }

    /// The test history alone, with its own navigation bar.
    @MainActor
    public static func makeHistoryViewController() -> UIViewController {
        UIHostingController(rootView: RmbtHistoryView())
    }

    /// Presents the start screen full screen over `presenter`, with a close button.
    @MainActor
    public static func present(from presenter: UIViewController, animated: Bool = true) {
        var controller: UIViewController?
        controller = makeViewController(onClose: { [weak presenter] in
            presenter?.dismiss(animated: true)
            controller = nil
        })
        guard let controller else { return }
        controller.modalPresentationStyle = .fullScreen
        presenter.present(controller, animated: animated)
    }
}

/// Texts and number formats in the configured language.
enum L10n {
    static func tr(_ key: String) -> String {
        NSLocalizedString(key, bundle: bundle, comment: "")
    }

    static func tr(_ key: String, _ args: CVarArg...) -> String {
        String(format: tr(key), locale: locale, arguments: args)
    }

    /// The `.lproj` of the configured language, or the SDK bundle (system choice) when none is set.
    static var bundle: Bundle {
        guard let language = RmbtUI.config.language,
              let path = Bundle.module.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return .module }
        return bundle
    }

    /// For the decimal separator ("23,9" in Russian), whatever the region set on the device.
    static var locale: Locale {
        Locale(identifier: RmbtUI.config.language ?? Bundle.module.preferredLocalizations.first ?? "en")
    }

    static var appName: String {
        RmbtUI.config.appName
            ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
            ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String
            ?? ""
    }
}
