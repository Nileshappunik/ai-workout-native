// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AiWorkoutNative",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "AiWorkoutNative",
            targets: ["AIWorkoutNativePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "AIWorkoutNativePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/AIWorkoutNativePlugin"),
        .testTarget(
            name: "AIWorkoutNativePluginTests",
            dependencies: ["AIWorkoutNativePlugin"],
            path: "ios/Tests/AIWorkoutNativePluginTests")
    ]
)