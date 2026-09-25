import AVFoundation
import Foundation
import Capacitor
import CoreAudio

enum MyError: Error {
    case runtimeError(String)
}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(NativeAudio)
public class NativeAudio: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NativeAudio"
    public let jsName = "NativeAudio"
    // Every method stays synchronous: play, pause, resume, stop, loop and unload change the players in the order of
    // the calls, which async methods would not keep.
    public let pluginMethods: [CAPPluginMethod] = [
        .promise("configure", NativeAudio.configure),
        .promise("preload", NativeAudio.preload),
        .promise("play", NativeAudio.play),
        .promise("stop", NativeAudio.stop),
        .promise("loop", NativeAudio.loop),
        .promise("pause", NativeAudio.pause),
        .promise("resume", NativeAudio.resume),
        .promise("unload", NativeAudio.unload),
        .promise("setVolume", NativeAudio.setVolume),
        .promise("getCurrentTime", NativeAudio.getCurrentTime),
        .promise("getDuration", NativeAudio.getDuration),
        .promise("isPlaying", NativeAudio.isPlaying)
    ]

    var audioList: [String: Any] = [:]
    var fadeMusic = false
    var session = AVAudioSession.sharedInstance()

    override public func load() {
        super.load()

        self.fadeMusic = false

        do {
            try self.session.setCategory(AVAudioSession.Category.playback)
            try self.session.setActive(false)
        } catch {
            print("Failed to set session category")
        }
    }

    func configure(_ call: CAPPluginCall) {
        self.fadeMusic = call.getBool(Constant.FadeKey, false)
        do {
            if call.getBool(Constant.FocusAudio, false) {
                try self.session.setCategory(AVAudioSession.Category.playback)
            } else {
                try self.session.setCategory(AVAudioSession.Category.ambient)
            }
        } catch {
            print("Failed to set setCategory audio")
        }
        call.resolve()
    }

    func preload(_ call: CAPPluginCall) {
        preloadAsset(call, isComplex: true)
    }

    func play(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        let time = call.getDouble("time") ?? 0
        if audioId != "" {
            let queue = DispatchQueue(label: "com.getcapacitor.community.audio.complex.queue", qos: .userInitiated)

            queue.async {
                if self.audioList.count > 0 {
                    let asset = self.audioList[audioId]

                    if asset != nil {
                        if asset is AudioAsset {
                            let audioAsset = asset as? AudioAsset

                            if self.fadeMusic {
                                audioAsset?.playWithFade(time: time)
                            } else {
                                audioAsset?.play(time: time)
                            }
                            call.resolve()
                        } else if asset is Int32 {
                            let audioAsset = asset as? NSNumber ?? 0
                            AudioServicesPlaySystemSound(SystemSoundID(audioAsset.intValue ))
                            call.resolve()
                        } else {
                            call.reject(Constant.ErrorAssetNotFound)
                        }
                    }
                }
            }
        }
    }

    /// The loaded asset the call names; throws when the call names none or it is not loaded.
    private func getAudioAsset(_ call: CAPPluginCall) throws -> AudioAsset {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        if audioId == "" {
            throw CAPPluginError(Constant.ErrorAssetId)
        }
        if let audioAsset = self.audioList[audioId] as? AudioAsset {
            return audioAsset
        }
        throw CAPPluginError(Constant.ErrorAssetNotFound + " - " + audioId)
    }

    func getDuration(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        call.resolve([
            "duration": audioAsset.getDuration()
        ])
    }

    func getCurrentTime(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        call.resolve([
            "currentTime": audioAsset.getCurrentTime()
        ])
    }

    func resume(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        audioAsset.resume()
        call.resolve()
    }

    func pause(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        audioAsset.pause()
        call.resolve()
    }

    func stop(_ call: CAPPluginCall) throws {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""

        do {
            try stopAudio(audioId: audioId)
        } catch {
            throw CAPPluginError(Constant.ErrorAssetNotFound)
        }
        call.resolve()
    }

    func loop(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        audioAsset.loop()
        call.resolve()
    }

    func unload(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        if self.audioList.count > 0 {
            let asset = self.audioList[audioId]
            if asset != nil && asset is AudioAsset {
                let audioAsset = asset as! AudioAsset
                audioAsset.unload()
                self.audioList[audioId] = nil
            }
        }
        call.resolve()
    }

    func setVolume(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        let volume = call.getFloat(Constant.Volume) ?? 1.0

        audioAsset.setVolume(volume: volume as NSNumber)
        call.resolve()
    }

    func isPlaying(_ call: CAPPluginCall) throws {
        let audioAsset = try self.getAudioAsset(call)

        call.resolve([
            "isPlaying": audioAsset.isPlaying()
        ])
    }

    private func preloadAsset(_ call: CAPPluginCall, isComplex complex: Bool) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        let channels: NSNumber?
        let volume: Float?
        let delay: NSNumber?
        let isUrl: Bool?

        if audioId != "" {
            let assetPath: String = call.getString(Constant.AssetPathKey) ?? ""

            if complex {
                volume = call.getFloat("volume") ?? 1.0
                channels = NSNumber(value: call.getInt("channels") ?? 1)
                delay = NSNumber(value: call.getInt("delay") ?? 1)
                isUrl = call.getBool("isUrl") ?? false
            } else {
                channels = 0
                volume = 0
                delay = 0
                isUrl = false
            }

            if audioList.isEmpty {
                audioList = [:]
            }

            let asset = audioList[audioId]
            let queue = DispatchQueue(label: "com.getcapacitor.community.audio.simple.queue", qos: .userInitiated)

            queue.async {
                if asset == nil {
                    var basePath: String?
                    if isUrl == false {
                        let assetPathSplit = assetPath.components(separatedBy: ".")
                        basePath = Bundle.main.path(forResource: assetPathSplit[0], ofType: assetPathSplit[1])
                    } else {
                        let url = URL(string: assetPath)
                        basePath = url!.path
                    }

                    if FileManager.default.fileExists(atPath: basePath ?? "") {
                        if !complex {
                            let pathUrl = URL(fileURLWithPath: basePath ?? "")
                            let soundFileUrl: CFURL = CFBridgingRetain(pathUrl) as! CFURL
                            var soundId = SystemSoundID()
                            AudioServicesCreateSystemSoundID(soundFileUrl, &soundId)
                            self.audioList[audioId] = NSNumber(value: Int32(soundId))
                            call.resolve()
                        } else {
                            let audioAsset: AudioAsset = AudioAsset(owner: self, withAssetId: audioId, withPath: basePath, withChannels: channels, withVolume: volume as NSNumber?, withFadeDelay: delay)
                            self.audioList[audioId] = audioAsset
                            call.resolve()
                        }
                    } else {
                        call.reject(Constant.ErrorAssetPath + " - " + assetPath)
                    }
                } else {
                    call.reject(Constant.ErrorAssetExists)
                }
            }
        }
    }

    private func stopAudio(audioId: String) throws {
        if self.audioList.count > 0 {
            let asset = self.audioList[audioId]

            if asset != nil {
                if asset is AudioAsset {
                    let audioAsset = asset as? AudioAsset

                    if self.fadeMusic {
                        audioAsset?.playWithFade(time: audioAsset?.getCurrentTime() ?? 0)
                    } else {
                        audioAsset?.stop()
                    }
                }
            } else {
                throw MyError.runtimeError(Constant.ErrorAssetNotFound)
            }
        }
    }
}
