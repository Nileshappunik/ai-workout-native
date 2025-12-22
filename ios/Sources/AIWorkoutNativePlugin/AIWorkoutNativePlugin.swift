import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(AIWorkoutNativePlugin)
public class AIWorkoutNativePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AIWorkoutNativePlugin"
    public let jsName = "AIWorkoutNative"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = AIWorkoutNative()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
}
