package com.getcapacitor.community.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.util.concurrent.Callable

public class AudioDispatcher
@Throws(Exception::class)
constructor(assetFileDescriptor: AssetFileDescriptor, volume: Float) :
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnSeekCompleteListener {
    private val mediaPlayer = MediaPlayer()
    private var mediaState = INVALID
    private var owner: AudioAsset? = null

    init {
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
        mediaPlayer.setOnSeekCompleteListener(this)
        mediaPlayer.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        mediaPlayer.setVolume(volume, volume)
        mediaPlayer.prepare()
    }

    public fun setOwner(asset: AudioAsset?) {
        owner = asset
    }

    public val duration: Double
        get() = mediaPlayer.duration / 1000.0

    public val currentPosition: Double
        get() = mediaPlayer.currentPosition / 1000.0

    @Throws(Exception::class)
    public fun play(time: Double, callable: Callable<Void?>) {
        invokePlay(time, false)
        callable.call()
    }

    @Throws(Exception::class)
    public fun pause(): Boolean {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            mediaState = PAUSE
            return true
        }

        return false
    }

    @Throws(Exception::class)
    public fun resume() {
        mediaPlayer.start()
    }

    @Throws(Exception::class)
    public fun stop() {
        if (mediaPlayer.isPlaying) {
            mediaState = INVALID
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
        }
    }

    @Throws(Exception::class)
    public fun setVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    @Throws(Exception::class)
    public fun loop() {
        mediaPlayer.isLooping = true
    }

    @Throws(Exception::class)
    public fun unload() {
        stop()
        mediaPlayer.release()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        try {
            if (mediaState != LOOPING) {
                mediaState = INVALID

                stop()

                owner?.dispatchComplete()
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Caught exception while listening for onCompletion: " + ex.localizedMessage)
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        try {
            when (mediaState) {
                PENDING_PLAY -> mediaPlayer.isLooping = false
                PENDING_LOOP -> mediaPlayer.isLooping = true
                else -> mediaState = PREPARED
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Caught exception while listening for onPrepared: " + ex.localizedMessage)
        }
    }

    private fun seek(time: Double) {
        mediaPlayer.seekTo((time * 1000).toInt().toLong(), MediaPlayer.SEEK_NEXT_SYNC)
    }

    private fun invokePlay(time: Double, loop: Boolean) {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                mediaPlayer.isLooping = loop
                mediaState = PENDING_PLAY
                seek(time)
            } else if (mediaState == PREPARED) {
                mediaState = if (loop) PENDING_LOOP else PENDING_PLAY
                onPrepared(mediaPlayer)
                seek(time)
            } else {
                mediaState = if (loop) PENDING_LOOP else PENDING_PLAY
                mediaPlayer.isLooping = loop
                seek(time)
            }
        } catch (ex: Exception) {
            Log.d(TAG, "Caught exception while invoking audio: " + ex.localizedMessage)
        }
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        if (mediaState == PENDING_PLAY || mediaState == PENDING_LOOP) {
            Log.w("AudioDispatcher", "play $mediaState")
            mediaPlayer.start()
            mediaState = PLAYING
        }
    }

    @get:Throws(Exception::class)
    public val isPlaying: Boolean
        get() = mediaPlayer.isPlaying

    private companion object {
        private const val TAG = "AudioDispatcher"

        private const val INVALID = 0
        private const val PREPARED = 1
        private const val PENDING_PLAY = 2
        private const val PLAYING = 3
        private const val PENDING_LOOP = 4
        private const val LOOPING = 5
        private const val PAUSE = 6
    }
}
