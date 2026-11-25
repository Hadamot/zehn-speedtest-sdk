// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RmbtSdk",
    platforms: [.iOS(.v15)],
    products: [.library(name: "RmbtSdk", targets: ["RmbtSdk"])],
    targets: [
        .binaryTarget(
            name: "RmbtSdk",
            url: "https://github.com/Hadamot/zehn-speedtest-sdk/releases/download/v0.1.2/RmbtSdk.xcframework.zip",
            checksum: "62b9f9cbf97da50def7b7547728cf31802edd23f657e88fd3dc2375519b7753a"
        ),
    ]
)
