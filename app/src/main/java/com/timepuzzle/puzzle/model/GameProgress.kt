package com.timepuzzle.puzzle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

@Serializable
data class LevelResult(
    var isCompleted: Boolean = false,
    var bestTime: Double? = null,
    var fewestMoves: Int? = null
)

@Serializable
data class GameSettings(
    var musicEnabled: Boolean = true,
    var soundEnabled: Boolean = true,
    var hapticsEnabled: Boolean = true,
    var unlimitedEnergy: Boolean = true
)

@Serializable
data class GameProgress(
    var schemaVersion: Int = 1,
    var completedLevels: Map<Int, LevelResult> = emptyMap(),
    var coins: Int = 0,
    var energy: Int = MAX_ENERGY,
    var energyUpdatedAt: Long? = null,
    var settings: GameSettings = GameSettings(),
    var hintDayKey: String = "",
    var hintsRemaining: Int = 3
) {
    fun refreshEnergy(now: Long = System.currentTimeMillis()): GameProgress {
        if (settings.unlimitedEnergy) { return this.copy(energyUpdatedAt = now) }
        val last = energyUpdatedAt ?: run { return this.copy(energyUpdatedAt = now) }
        if (energy >= MAX_ENERGY) { return this.copy(energyUpdatedAt = now) }
        val recovered = ((now - last) / ENERGY_RECOVERY_MS).toInt()
        return if (recovered > 0) {
            this.copy(energy = minOf(MAX_ENERGY, energy + recovered), energyUpdatedAt = now)
        } else this
    }

    fun isUnlocked(level: LevelDefinition): Boolean =
        level.unlockRequirement == 0 ||
        completedLevels.values.count { it.isCompleted } >= level.unlockRequirement

    fun recordCompletion(level: LevelDefinition, elapsed: Double, moves: Int): GameProgress {
        val prev = completedLevels[level.id] ?: LevelResult()
        val first = !prev.isCompleted
        val result = prev.copy(
            isCompleted = true,
            bestTime = minOf(prev.bestTime ?: elapsed, elapsed),
            fewestMoves = minOf(prev.fewestMoves ?: moves, moves)
        )
        return this.copy(
            completedLevels = completedLevels + (level.id to result),
            coins = if (first) coins + level.rewardCoins else coins
        )
    }

    fun consumeHint(now: Long = System.currentTimeMillis()): GameProgress? {
        val today = dayKey(now)
        val remaining = if (hintDayKey != today) 3 else hintsRemaining
        if (remaining <= 0) return null
        return this.copy(hintDayKey = today, hintsRemaining = remaining - 1)
    }

    val availableHints: Int get() = if (hintDayKey == dayKey(System.currentTimeMillis())) hintsRemaining else 3

    fun toJson(): String = format.encodeToString(GameProgress.serializer(), this)

    companion object {
        const val MAX_ENERGY = 5
        const val ENERGY_RECOVERY_MS = 10 * 60 * 1000L
        val format = Json { ignoreUnknownKeys = true }

        fun fromJson(s: String?): GameProgress {
            if (s.isNullOrBlank()) return GameProgress()
            return try { format.decodeFromString(s) } catch (_: Exception) { GameProgress() }
        }

        fun dayKey(ts: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }
    }
}
