package com.timepuzzle.puzzle.data

import android.content.Context
import com.timepuzzle.puzzle.model.LevelDefinition
import com.timepuzzle.puzzle.model.LevelListSerializer

interface LevelRepository {
    fun loadLevels(): List<LevelDefinition>
}

class AssetLevelRepository(private val context: Context) : LevelRepository {
    override fun loadLevels(): List<LevelDefinition> {
        return try {
            context.assets.open("levels.json").bufferedReader().use { LevelListSerializer.parse(it.readText()) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
