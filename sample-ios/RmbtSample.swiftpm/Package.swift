// swift-tools-version:5.9
// Demo app for the iOS speed test screens (not published). Open this folder in Xcode, or:
// RMBT_LOCAL_XCFRAMEWORK=1 xcodebuild -scheme RmbtSample -destination 'platform=iOS Simulator,name=iPhone 17' build
import AppleProductTypes
import PackageDescription

let package = Package(
    name: "RmbtSample",
    defaultLocalization: "en",
    platforms: [.iOS("16.0")],
    products: [
        .iOSApplication(
            name: "RmbtSample",
            targets: ["RmbtSample"],
            bundleIdentifier: "tj.zehn.rmbt.sample",
            displayVersion: "1.0",
            bundleVersion: "1",
            appIcon: .placeholder(icon: .gamepad),
            accentColor: .presetColor(.blue),
            supportedDeviceFamilies: [.phone, .pad],
            supportedInterfaceOrientations: [.portrait, .landscapeLeft, .landscapeRight],
            capabilities: [
                .locationWhenInUse(purposeString: "Shows where the speed test ran."),
            ]
        ),
    ],
    dependencies: [
        .package(name: "RmbtSdk", path: "../.."),
    ],
    targets: [
        .executableTarget(
            name: "RmbtSample",
            dependencies: [.product(name: "RmbtSdkUI", package: "RmbtSdk")],
            path: "Sources",
            resources: [.process("Resources")],
            // App packages don't look in Frameworks/, where the core's dynamic framework is embedded (Xcode
            // app projects do by default).
            linkerSettings: [.unsafeFlags(["-Xlinker", "-rpath", "-Xlinker", "@executable_path/Frameworks"])]
        ),
    ]
)
