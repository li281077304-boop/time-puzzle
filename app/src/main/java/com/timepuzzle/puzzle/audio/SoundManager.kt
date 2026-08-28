package com.timepuzzle.puzzle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.timepuzzle.puzzle.R

/**
 * 简易音效管理：拼块放对、拼块连接、通关三个音效。
 * 用 SoundPool 加载 res/raw 或 assets 下的 wav。
 */
class SoundManager(private val context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val placeCorrectId: Int = pool.load(context, R.raw.place_correct, 1)
    private val joinId: Int = pool.load(context, R.raw.join, 1)
    private val completeId: Int = pool.load(context, R.raw.complete, 1)

    fun playPlaceCorrect() = play(placeCorrectId)
    fun playJoin() = play(joinId)
    fun playComplete() = play(completeId)

    private fun play(id: Int) {
        pool.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun release() = pool.release()
}