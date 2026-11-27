// swift-tools-version:5.9
import Foundation
import PackageDescription

// Local development: when `./gradlew :rmbt-sdk:assembleRmbtSdkReleaseXCFramework` has built the core next to
// this file (e.g. an app referencing this folder as a local package), that build is used instead of the
// released one. A clone from git has no `core/build`, so apps adding the package by URL get the release.
// RMBT_RELEASED_CORE=1 forces the release, RMBT_LOCAL_XCFRAMEWORK=1 the local build.
let localCorePath = "core/build/XCFrameworks/release/RmbtSdk.xcframework"
let environment = ProcessInfo.processInfo.environment
let localCore = environment["RMBT_LOCAL_XCFRAMEWORK"] != nil || (
    environment["RMBT_RELEASED_CORE"] == nil
        && FileManager.default.fileExists(atPath: Context.packageDirectory + "/" + localCorePath)
)

let core: Target = localCore
    ? .binaryTarget(name: "RmbtSdk", path: localCorePath)
    : .binaryTarget(
        name: "RmbtSdk",
        url: "https://github.com/Hadamot/zehn-speedtest-sdk/releases/download/v0.1.2/RmbtSdk.xcframework.zip",
        checksum: "62b9f9cbf97da50def7b7547728cf31802edd23f657e88fd3dc2375519b7753a"
    )

let package = Package(
    name: "RmbtSdk",
    defaultLocalization: "en",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "RmbtSdk", targets: ["RmbtSdk"]),
        .library(name: "RmbtSdkUI", targets: ["RmbtSdkUI"]),
    ],
    targets: [
        core,
        .target(
            name: "RmbtSdkUI",
            dependencies: ["RmbtSdk"],
            path: "ui-ios/Sources/RmbtSdkUI",
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "RmbtSdkUITests",
            dependencies: ["RmbtSdkUI"],
            path: "ui-ios/Tests/RmbtSdkUITests"
        ),
    ]
)
