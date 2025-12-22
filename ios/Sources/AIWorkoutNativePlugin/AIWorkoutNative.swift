import Foundation

@objc public class AIWorkoutNative: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
