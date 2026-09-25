import XCTest
import Capacitor
@testable import NativeAudio

class PluginTests: XCTestCase {
    func testPluginIsRegisteredUnderItsJavaScriptName() {
        let plugin = NativeAudio()

        XCTAssertEqual(plugin.identifier, "NativeAudio")
        XCTAssertEqual(plugin.jsName, "NativeAudio")
    }

    func testPluginExposesItsMethodsAsPromises() {
        let plugin = NativeAudio()

        XCTAssertEqual(plugin.pluginMethods.map(\.name), [
            "configure",
            "preload",
            "play",
            "stop",
            "loop",
            "pause",
            "resume",
            "unload",
            "setVolume",
            "getCurrentTime",
            "getDuration",
            "isPlaying"
        ])
        XCTAssertTrue(plugin.pluginMethods.allSatisfy { $0.returnType == .promise })
    }

    func testMethodsOnAnAssetThrowWithoutAnAssetId() {
        let plugin = NativeAudio()
        let methods: [(String, (CAPPluginCall) throws -> Void)] = [
            ("getDuration", plugin.getDuration),
            ("getCurrentTime", plugin.getCurrentTime),
            ("resume", plugin.resume),
            ("pause", plugin.pause),
            ("loop", plugin.loop),
            ("setVolume", plugin.setVolume),
            ("isPlaying", plugin.isPlaying)
        ]
        for (name, method) in methods {
            XCTAssertEqual(thrownError(method, name, [:])?.message, "Asset Id is missing", name)
        }
    }

    func testMethodsOnAnAssetThrowForAnAssetThatIsNotLoaded() {
        let plugin = NativeAudio()
        let error = thrownError(plugin.getDuration, "getDuration", ["assetId": "missing"])
        XCTAssertEqual(error?.message, "Asset is not loaded - missing")
        XCTAssertNil(error?.code)
    }

    func testStopThrowsForAnAssetThatIsNotLoaded() {
        let plugin = NativeAudio()
        plugin.audioList["other"] = NSNumber(value: 1)
        XCTAssertEqual(thrownError(plugin.stop, "stop", ["assetId": "missing"])?.message, "Asset is not loaded")
    }

    /// The error `method` throws, which the bridge rejects the call with; nil when it does not throw.
    private func thrownError(_ method: (CAPPluginCall) throws -> Void, _ name: String, _ options: JSObject) -> CAPPluginError? {
        let call = CAPPluginCall(callbackId: "test", methodName: name, options: options, success: { _, _ in
            XCTFail("\(name) must not resolve")
        }, error: { _ in
            XCTFail("\(name) answers by throwing")
        })
        do {
            try method(call)
            XCTFail("\(name) must throw")
            return nil
        } catch {
            return error as? CAPPluginError
        }
    }
}
