package com.getcapacitor.community.audio

import android.Manifest
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginException
import com.getcapacitor.PluginMethod
import com.getcapacitor.PluginThread
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.community.audio.Constant.ASSET_ID
import com.getcapacitor.community.audio.Constant.ASSET_PATH
import com.getcapacitor.community.audio.Constant.AUDIO_CHANNEL_NUM
import com.getcapacitor.community.audio.Constant.ERROR_ASSET_NOT_LOADED
import com.getcapacitor.community.audio.Constant.ERROR_ASSET_PATH_MISSING
import com.getcapacitor.community.audio.Constant.ERROR_AUDIO_ASSET_MISSING
import com.getcapacitor.community.audio.Constant.ERROR_AUDIO_EXISTS
import com.getcapacitor.community.audio.Constant.ERROR_AUDIO_ID_MISSING
import com.getcapacitor.community.audio.Constant.LOOP
import com.getcapacitor.community.audio.Constant.OPT_FOCUS_AUDIO
import com.getcapacitor.community.audio.Constant.VOLUME
import java.io.File
import java.net.URI
import java.util.concurrent.Callable

@CapacitorPlugin(
    permissions = [
        Permission(strings = [Manifest.permission.MODIFY_AUDIO_SETTINGS]),
        Permission(strings = [Manifest.permission.WRITE_EXTERNAL_STORAGE]),
        Permission(strings = [Manifest.permission.READ_PHONE_STATE])
    ]
)
public class NativeAudio :
    Plugin(),
    AudioManager.OnAudioFocusChangeListener {
    private var audioManager: AudioManager? = null

    override fun load() {
        super.load()

        audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    }

    override fun onAudioFocusChange(focusChange: Int) {}

    override fun handleOnPause() {
        super.handleOnPause()

        try {
            for (audio in audioAssetList.values) {
                if (audio.pause()) {
                    resumeList.add(audio)
                }
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Exception caught while listening for handleOnPause: " + ex.localizedMessage)
        }
    }

    override fun handleOnResume() {
        super.handleOnResume()

        try {
            while (resumeList.isNotEmpty()) {
                resumeList.removeAt(0).resume()
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Exception caught while listening for handleOnResume: " + ex.localizedMessage)
        }
    }

    @PluginMethod
    @Suppress("DEPRECATION")
    public fun configure(call: PluginCall) {
        audioManager?.let { audioManager ->
            if (call.getBoolean(OPT_FOCUS_AUDIO, false) == true) {
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            } else {
                audioManager.abandonAudioFocus(this)
            }
        }
        call.resolve()
    }

    @PluginMethod
    public fun preload(call: PluginCall) {
        Thread { preloadAsset(call) }.start()
    }

    // The players are driven from the main thread
    @PluginMethod(thread = PluginThread.MAIN)
    public fun play(call: PluginCall) {
        playOrLoop("play", call)
    }

    @PluginMethod
    public fun getCurrentTime(call: PluginCall) {
        val asset = getLoadedAsset(call)
        try {
            call.resolve(JSObject().put("currentTime", asset.currentPosition))
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun getDuration(call: PluginCall) {
        val asset = getLoadedAsset(call)
        try {
            call.resolve(JSObject().put("duration", asset.duration))
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod(thread = PluginThread.MAIN)
    public fun loop(call: PluginCall) {
        playOrLoop("loop", call)
    }

    @PluginMethod
    public fun pause(call: PluginCall) {
        val audioId = call.getString(ASSET_ID)
        val asset = audioAssetList[audioId] ?: throw PluginException("$ERROR_ASSET_NOT_LOADED - $audioId")
        try {
            if (asset.pause()) {
                resumeList.add(asset)
            }

            call.resolve()
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun resume(call: PluginCall) {
        val audioId = call.getString(ASSET_ID)
        val asset = audioAssetList[audioId] ?: throw PluginException("$ERROR_ASSET_NOT_LOADED - $audioId")
        try {
            asset.resume()
            resumeList.add(asset)
            call.resolve()
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun stop(call: PluginCall) {
        val audioId = call.getString(ASSET_ID)
        val asset = audioAssetList[audioId] ?: throw PluginException("$ERROR_ASSET_NOT_LOADED - $audioId")
        try {
            asset.stop()
            call.resolve()
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun unload(call: PluginCall) {
        try {
            val status = JSObject()
            val audioId = call.getString(ASSET_ID)

            if (isStringValid(audioId)) {
                val asset = audioAssetList[audioId]
                if (asset != null) {
                    asset.unload()
                    audioAssetList.remove(audioId)

                    status.put("status", "OK")
                } else {
                    status.put("status", "$ERROR_AUDIO_ASSET_MISSING - $audioId")
                }
            } else {
                status.put("status", ERROR_AUDIO_ID_MISSING)
            }
            call.resolve(status)
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun setVolume(call: PluginCall) {
        try {
            val audioId = call.getString(ASSET_ID)
            // A missing volume was a NullPointerException in the Java implementation, caught below like this one.
            val volume = call.getFloat(VOLUME)!!

            val asset = audioAssetList[audioId]
            if (asset != null) {
                asset.setVolume(volume)
                call.resolve()
            } else {
                call.reject(ERROR_AUDIO_ASSET_MISSING)
            }
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    @PluginMethod
    public fun isPlaying(call: PluginCall) {
        val asset = getLoadedAsset(call)
        try {
            call.resolve(JSObject().put("isPlaying", asset.isPlaying))
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    public fun dispatchComplete(assetId: String?) {
        val ret = JSObject()
        ret.put("assetId", assetId)
        notifyListeners("complete", ret)
    }

    private fun preloadAsset(call: PluginCall) {
        val volume = call.getDouble(VOLUME, 1.0) ?: 1.0
        val audioChannelNum = call.getInt(AUDIO_CHANNEL_NUM, 1) ?: 1

        try {
            val audioId = call.getString(ASSET_ID)

            val isUrl = call.getBoolean("isUrl", false) ?: false

            if (audioId == null || !isStringValid(audioId)) {
                call.reject("$ERROR_AUDIO_ID_MISSING - $audioId")
                return
            }

            if (audioAssetList.containsKey(audioId)) {
                call.reject(ERROR_AUDIO_EXISTS)
                return
            }

            val assetPath = call.getString(ASSET_PATH)

            if (assetPath == null || !isStringValid(assetPath)) {
                call.reject("$ERROR_ASSET_PATH_MISSING - $audioId - $assetPath")
                return
            }

            val assetFileDescriptor: AssetFileDescriptor? =
                if (isUrl) {
                    val f = File(URI(assetPath))
                    val p = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    AssetFileDescriptor(p, 0, -1)
                } else if (assetPath.startsWith("content")) {
                    activity.contentResolver.openAssetFileDescriptor(Uri.parse(assetPath), "r")
                } else {
                    activity.applicationContext.resources.assets
                        .openFd(assetPath)
                }

            val asset = AudioAsset(this, audioId, assetFileDescriptor, audioChannelNum, volume.toFloat())
            audioAssetList[audioId] = asset

            val status = JSObject()
            status.put("STATUS", "OK")
            call.resolve(status)
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    private fun playOrLoop(action: String, call: PluginCall) {
        try {
            val audioId = call.getString(ASSET_ID)
            val time = call.getDouble("time", 0.0) ?: 0.0
            val asset = audioAssetList[audioId] ?: return
            if (LOOP == action) {
                asset.loop()
            } else {
                asset.play(
                    time,
                    Callable<Void?> {
                        call.resolve()
                        null
                    }
                )
            }
        } catch (ex: Exception) {
            call.reject(ex.message)
        }
    }

    /**
     * The loaded asset the call names; throws when the call names none or it is not loaded.
     */
    private fun getLoadedAsset(call: PluginCall): AudioAsset {
        val audioId = call.getString(ASSET_ID)
        if (!isStringValid(audioId)) {
            throw PluginException("$ERROR_AUDIO_ID_MISSING - $audioId")
        }
        return audioAssetList[audioId] ?: throw PluginException("$ERROR_AUDIO_ASSET_MISSING - $audioId")
    }

    private fun isStringValid(value: String?): Boolean = !value.isNullOrEmpty() && value != "null"

    public companion object {
        public const val TAG: String = "NativeAudio"

        // Shared by every instance of the plugin, as the static fields of the Java implementation were.
        private val audioAssetList = HashMap<String, AudioAsset>()
        private val resumeList = ArrayList<AudioAsset>()
    }
}
