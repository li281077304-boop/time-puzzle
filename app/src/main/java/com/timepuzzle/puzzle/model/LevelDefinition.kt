package com.timepuzzle.puzzle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LevelDefinition(
    val id: Int,
    val title: String,
    val image: String,
    val rows: Int,
    val columns: Int,
    val timeLimit: Int,
    val unlockRequirement: Int,
    val rewardCoins: Int,
    val snapDistanceRatio: Double? = null,
    val autoPlaceOnRelease: Boolean = false,
    val group: String? = null
) {
    val grid: PuzzleGrid get() = PuzzleGrid(rows, columns)
    val effectiveSnapDistanceRatio: Double get() = snapDistanceRatio ?: 0.12
    val groupName: String get() = group ?: "主线"
}

object LevelListSerializer {
    val format = Json { ignoreUnknownKeys = true }
    fun parse(json: String): List<LevelDefinition> = format.decodeFromString(json)
}
