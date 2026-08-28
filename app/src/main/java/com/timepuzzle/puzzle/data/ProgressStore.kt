package com.timepuzzle.puzzle.data

import android.content.Context
import com.timepuzzle.puzzle.model.GameProgress
import java.io.File

interface ProgressStoring {
    fun load(): GameProgress
    fun save(p: GameProgress)
    fun reset()
}

class FileProgressStore(private val context: Context) : ProgressStoring {
    private val file = File(context.filesDir, "PuzzleGameProgress.json")

    override fun load(): GameProgress =
        if (file.exists()) GameProgress.fromJson(file.readText()) else GameProgress()

    override fun save(p: GameProgress) {
        // 原子写入：先写临时文件，再改名。进程被杀时不会留下空/半残的 JSON。
        val temp = File(context.filesDir, "PuzzleGameProgress.json.tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(p.toJson().toByteArray())
                stream.fd.sync()
            }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (_: Exception) {
            temp.delete()
        }
    }

    override fun reset() {
        try { file.delete() } catch (_: Exception) { }
    }
}
