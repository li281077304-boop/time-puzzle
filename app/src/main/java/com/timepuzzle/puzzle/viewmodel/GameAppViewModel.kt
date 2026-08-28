package com.timepuzzle.puzzle.viewmodel

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.timepuzzle.puzzle.data.AssetLevelRepository
import com.timepuzzle.puzzle.data.FileProgressStore
import com.timepuzzle.puzzle.model.GameProgress
import com.timepuzzle.puzzle.model.GameSettings
import com.timepuzzle.puzzle.model.LevelDefinition

class GameAppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AssetLevelRepository(app)
    private val store = FileProgressStore(app)

    val levels: List<LevelDefinition> = repo.loadLevels()

    private val _progress: MutableState<GameProgress> = mutableStateOf(store.load())
    val progress: GameProgress get() = _progress.value
    val progressState: androidx.compose.runtime.State<GameProgress> get() = _progress

    /** 同一分组内、id 大于当前关的最近一关；没有则返回 null。 */
    fun nextLevel(level: LevelDefinition): LevelDefinition? =
        levels.filter { it.groupName == level.groupName && it.id > level.id }
            .minByOrNull { it.id }

    fun isUnlocked(level: LevelDefinition): Boolean {
        _progress.value = _progress.value.refreshEnergy()
        return _progress.value.isUnlocked(level)
    }

    fun canStart(level: LevelDefinition): Boolean {
        _progress.value = _progress.value.refreshEnergy()
        return _progress.value.settings.unlimitedEnergy || _progress.value.energy > 0
    }

    fun start(level: LevelDefinition) {
        if (!isUnlocked(level) || !canStart(level)) return
        val current = _progress.value
        if (!current.settings.unlimitedEnergy) {
            _progress.value = current.copy(
                energy = maxOf(0, current.energy - 1),
                energyUpdatedAt = System.currentTimeMillis()
            )
        }
        persist()
    }

    fun finish(level: LevelDefinition, elapsed: Double, moves: Int) {
        _progress.value = _progress.value.recordCompletion(level, elapsed, moves)
        persist()
    }

    fun consumeHint(): Boolean {
        val updated = _progress.value.consumeHint() ?: return false
        _progress.value = updated
        persist()
        return true
    }

    val availableHints: Int get() = _progress.value.availableHints

    fun updateSettings(block: (GameSettings) -> GameSettings) {
        _progress.value = _progress.value.copy(settings = block(_progress.value.settings))
        persist()
    }

    fun resetProgress() {
        store.reset()
        _progress.value = GameProgress()
    }

    private fun persist() {
        store.save(_progress.value)
    }
}
