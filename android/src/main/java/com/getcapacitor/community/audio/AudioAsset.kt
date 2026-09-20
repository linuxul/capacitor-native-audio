package com.getcapacitor.community.audio

import android.content.res.AssetFileDescriptor
import java.util.concurrent.Callable

public class AudioAsset
@Throws(Exception::class)
internal constructor(
    private val owner: NativeAudio,
    private val assetId: String,
    assetFileDescriptor: AssetFileDescriptor?,
    audioChannelNum: Int,
    volume: Float
) {
    private val audioList = ArrayList<AudioDispatcher>()
    private var playIndex = 0

    init {
        val channels = if (audioChannelNum < 0) 1 else audioChannelNum

        repeat(channels) {
            // A content URI that cannot be opened gives no descriptor. That was a NullPointerException here in the
            // Java implementation too, which preload reports as a rejected call.
            val audioDispatcher = AudioDispatcher(assetFileDescriptor!!, volume)
            audioList.add(audioDispatcher)
            if (channels == 1) audioDispatcher.setOwner(this)
        }
    }

    public fun dispatchComplete() {
        owner.dispatchComplete(assetId)
    }

    @Throws(Exception::class)
    public fun play(time: Double, callback: Callable<Void?>) {
        audioList[playIndex].play(time, callback)
        playIndex = (playIndex + 1) % audioList.size
    }

    public val duration: Double
        get() = if (audioList.size != 1) 0.0 else audioList[playIndex].duration

    public val currentPosition: Double
        get() = if (audioList.size != 1) 0.0 else audioList[playIndex].currentPosition

    @Throws(Exception::class)
    public fun pause(): Boolean {
        var wasPlaying = false

        for (audio in audioList) {
            wasPlaying = audio.pause() || wasPlaying
        }

        return wasPlaying
    }

    @Throws(Exception::class)
    public fun resume() {
        audioList.firstOrNull()?.resume()
    }

    @Throws(Exception::class)
    public fun stop() {
        for (audio in audioList) {
            audio.stop()
        }
    }

    @Throws(Exception::class)
    public fun loop() {
        audioList[playIndex].loop()
        playIndex = (playIndex + 1) % audioList.size
    }

    @Throws(Exception::class)
    public fun unload() {
        stop()

        for (audio in audioList) {
            audio.unload()
        }

        audioList.clear()
    }

    @Throws(Exception::class)
    public fun setVolume(volume: Float) {
        for (audio in audioList) {
            audio.setVolume(volume)
        }
    }

    @get:Throws(Exception::class)
    public val isPlaying: Boolean
        get() = audioList.size == 1 && audioList[playIndex].isPlaying
}
