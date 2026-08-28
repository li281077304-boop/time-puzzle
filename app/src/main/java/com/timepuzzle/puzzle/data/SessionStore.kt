package com.timepuzzle.puzzle.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 关卡中途存档：保存当前拼图到一半的状态（每块图块位置/zIndex、步数、已用时），
 * 大退/中途退出后重新进入可续玩。
 */
@Serializable
data class SessionSnapshot(
    val levelId: Int,
    val timeLimit: Int,
    val pieces: List<PiecePos>,
    val moveCount: Int,
    val elapsedSec: Double
) {
    @Serializable
    data class PiecePos(val row: Int, val col: Int, val x: Double, val y: Double, val z: Int)
}

class SessionStore(private val context: Context) {
    private val file = File(context.filesDir, "PuzzleGameSession.json")
    private val format = Json { ignoreUnknownKeys = true }

    fun load(levelId: Int): SessionSnapshot? {
        if (!file.exists()) return null
        return try {
            val s = format.decodeFromString<SessionSnapshot>(file.readText())
            if (s.levelId == levelId) s else null
        } catch (_: Exception) { null }
    }

    fun save(s: SessionSnapshot) {
        // Write a complete replacement first. A process kill during a normal
        // write must leave either the old save or the new save, never partial
        // JSON that silently loses the in-progress board on the next launch.
        val temp = File(context.filesDir, "PuzzleGameSession.json.tmp")
        try {
            temp.outputStream().use { stream ->
                stream.write(format.encodeToString(SessionSnapshot.serializer(), s).toByteArray())
                stream.fd.sync()
            }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (_: Exception) {
            temp.delete()
        }
    }

    fun clear() {
        try { file.delete() } catch (_: Exception) { }
    }
}
