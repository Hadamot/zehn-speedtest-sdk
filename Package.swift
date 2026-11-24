// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RmbtSdk",
    platforms: [.iOS(.v15)],
    products: [.library(name: "RmbtSdk", targets: ["RmbtSdk"])],
    targets: [
        .binaryTarget(
            name: "RmbtSdk",
            url: "https://github.com/Hadamot/zehn-speedtest-sdk/releases/download/v0.1.0/RmbtSdk.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
