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
}
