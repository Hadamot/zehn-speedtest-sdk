// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RmbtSdk",
    platforms: [.iOS(.v15)],
    products: [.library(name: "RmbtSdk", targets: ["RmbtSdk"])],
    targets: [
        .binaryTarget(
            name: "RmbtSdk",
            url: "https://github.com/Hadamot/zehn-speedtest-sdk/releases/download/v0.1.1/RmbtSdk.xcframework.zip",
            checksum: "44230bfb7078f7d007abb5c3403dae035fb8921cbaed2f5b9bc34d559c36ca22"
        ),
    ]
)
