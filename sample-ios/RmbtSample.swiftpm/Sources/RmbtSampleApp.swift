import RmbtSdkUI
import SwiftUI

/// The speed test screens as a host app shows them. `RMBT_LANG` (en, ru, tg) in the scheme's environment
/// switches the screens' language.
@main
struct RmbtSampleApp: App {
    init() {
        RmbtUI.configure(RmbtUiConfig(
            apiKey: "ios-sample",
            appName: "RmbtSample",
            language: ProcessInfo.processInfo.environment["RMBT_LANG"]
        ))
    }

    var body: some Scene {
        WindowGroup {
            RmbtSpeedtestView()
        }
    }
}
