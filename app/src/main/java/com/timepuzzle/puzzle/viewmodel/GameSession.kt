package com.timepuzzle.puzzle.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.timepuzzle.puzzle.audio.SoundManager
import com.timepuzzle.puzzle.data.SessionSnapshot
import com.timepuzzle.puzzle.data.SessionStore
import com.timepuzzle.puzzle.engine.PuzzleEngine
import com.timepuzzle.puzzle.engine.PuzzleMoveResult
import com.timepuzzle.puzzle.model.LevelDefinition
import com.timepuzzle.puzzle.model.PuzzlePieceID
import com.timepuzzle.puzzle.model.PuzzlePoint
import com.timepuzzle.puzzle.model.PuzzleSize
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class GameSession(
    private val level: LevelDefinition,
    private val soundManager: SoundManager? = null,
    private val sessionStore: SessionStore? = null
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

    private val _engine: MutableState<PuzzleEngine?> = mutableStateOf(null)
    val engine: PuzzleEngine? get() = _engine.value

    private val _remaining: MutableState<Int> = mutableStateOf(level.timeLimit)
    val remainingSeconds: Int get() = _remaining.value

    private val _complete: MutableState<Boolean> = mutableStateOf(false)
    val isComplete: Boolean get() = _complete.value

    private val _outOfTime: MutableState<Boolean> = mutableStateOf(false)
    val isOutOfTime: Boolean get() = _outOfTime.value

    private val _version: MutableState<Int> = mutableStateOf(0)
    val version: Int get() = _version.value

    /** 本步新"产生成新连接"的整组组（高光），本步新增"拼到正确位置"的图块（保留供底栏提示 UI 用，不再触发高光）。 */
    val newlyHomeIDs: Set<PuzzlePieceID> get() = _newlyHomeIDs
    private var _newlyHomeIDs: Set<PuzzlePieceID> = emptySet()
    val newlyJoinedIDs: Set<PuzzlePieceID> get() = _newlyJoinedIDs
    private var _newlyJoinedIDs: Set<PuzzlePieceID> = emptySet()

    private var startedAt: Long = 0
    private var elapsedSec: Double = 0.0
    private var timerJob: Job? = null
    private var lastWidth = 0.0
    private var lastHeight = 0.0

    fun configure(boardWidthPx: Double, boardHeightPx: Double) {
        lastWidth = boardWidthPx
        lastHeight = boardHeightPx
        if (_engine.value != null) return
        buildEngine(boardWidthPx, boardHeightPx)
    }

    fun restart() {
        timerJob?.cancel()
        _complete.value = false
        _outOfTime.value = false
        _remaining.value = level.timeLimit
        _newlyHomeIDs = emptySet()
        _newlyJoinedIDs = emptySet()
        sessionStore?.clear()
        buildEngine(lastWidth, lastHeight)
    }

    private fun buildEngine(w: Double, h: Double) {
        if (w <= 0 || h <= 0) return
        val snap = maxOf(20.0, w * level.effectiveSnapDistanceRatio)
        val eng = PuzzleEngine(level.grid, PuzzleSize(w, h), snap)

        val snapshot = sessionStore?.load(level.id)
        if (snapshot != null && snapshot.timeLimit == level.timeLimit && snapshot.pieces.size == eng.pieces.size) {
            val positions = mutableMapOf<PuzzlePieceID, PuzzlePoint>()
            val zIndexes = mutableMapOf<PuzzlePieceID, Int>()
            snapshot.pieces.forEach { sp ->
                val id = PuzzlePieceID(sp.row, sp.col)
                positions[id] = PuzzlePoint(sp.x, sp.y)
                zIndexes[id] = sp.z
            }
            eng.restorePositions(positions, zIndexes, snapshot.moveCount)
            startedAt = System.currentTimeMillis() - (snapshot.elapsedSec * 1000).toLong()
        } else {
            eng.shuffle()
            startedAt = System.currentTimeMillis()
        }

        _engine.value = eng
        startTimer()
        _version.value++
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = launch {
            while (true) {
                delay(1000)
                val left = maxOf(0, level.timeLimit - ((System.currentTimeMillis() - startedAt) / 1000).toInt())
                _remaining.value = left
                if (left <= 0) {
                    _outOfTime.value = true
                    break
                }
            }
        }
    }

    fun begin(pieceID: PuzzlePieceID): Set<PuzzlePieceID> {
        val eng = _engine.value ?: return emptySet()
        return try { eng.beginDrag(pieceID) } catch (_: Exception) { emptySet() }
    }

    fun drag(translation: PuzzlePoint) {
        _engine.value?.updateActiveDrag(translation)
    }

    fun endDrag(translation: PuzzlePoint? = null, forceSnap: Boolean? = null): PuzzleMoveResult? {
        val eng = _engine.value ?: return null
        // 拖动前记录连接对，作为（新）连接的基线
        val joinsBefore = joinedPairSet(eng)
        val result = try {
            eng.endActiveDrag(forceSnap ?: level.autoPlaceOnRelease, translation)
        } catch (_: Exception) { return null }

        if (result.didCompleteLevel) {
            elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0
            _complete.value = true
            timerJob?.cancel()
            sessionStore?.clear()
            soundManager?.playComplete()
        }

        // 高光逻辑：只闪"有新的连接形成"的情况。
        // 不闪"放对了位置"（不剧透答案）。
        // 一旦有新增连接，整组组都一起闪，提示玩家"这两块连上了"。
        val joinsAfter = joinedPairSet(eng)
        val newPairs = joinsAfter - joinsBefore
        if (newPairs.isNotEmpty()) {
            val anchor = newPairs.first().first
            _newlyJoinedIDs = connectedComponent(eng, anchor)
            soundManager?.playJoin()
        } else {
            _newlyJoinedIDs = emptySet()
        }
        _newlyHomeIDs = emptySet()

        if (!_complete.value) saveSnapshot()
        _version.value++
        return result
    }

    fun applyHint(): PuzzleMoveResult? {
        val eng = _engine.value ?: return null
        // 随机挑几块没拼对的，吸附成一起（不剧透答案）
        val misplaced = eng.pieces.filter { it.position.distance(it.homePosition) > 0.5 }
        if (misplaced.size < 2) return null
        val targets = misplaced.shuffled().take(3).map { it.id }
        eng.placeCluster(targets)
        if (eng.isCompleted) {
            elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0
            _complete.value = true
            timerJob?.cancel()
            sessionStore?.clear()
            soundManager?.playComplete()
        }
        _version.value++
        return PuzzleMoveResult(targets.last(), didSnap = true, didCompleteLevel = eng.isCompleted)
    }

    /** 扫描棋盘上的当前连接对集合（无序对）。 */
    private fun joinedPairSet(eng: PuzzleEngine): Set<Pair<PuzzlePieceID, PuzzlePieceID>> {
        val result = mutableSetOf<Pair<PuzzlePieceID, PuzzlePieceID>>()
        for (p in eng.pieces) {
            if (p.joinedEdges.right) {
                val a = p.id
                val b = PuzzlePieceID(p.id.row, p.id.column + 1)
                result.add(if (a < b) a to b else b to a)
            }
            if (p.joinedEdges.bottom) {
                val a = p.id
                val b = PuzzlePieceID(p.id.row + 1, p.id.column)
                result.add(if (a < b) a to b else b to a)
            }
        }
        return result
    }

    /** 从某块出发，按 joinedEdges 拓展出它所在的所有连接组。 */
    private fun connectedComponent(eng: PuzzleEngine, start: PuzzlePieceID): Set<PuzzlePieceID> {
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<PuzzlePieceID>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val p = eng.pieces.firstOrNull { it.id == cur } ?: continue
            val candidates = buildList {
                if (p.joinedEdges.top) add(PuzzlePieceID(p.id.row - 1, p.id.column))
                if (p.joinedEdges.bottom) add(PuzzlePieceID(p.id.row + 1, p.id.column))
                if (p.joinedEdges.left) add(PuzzlePieceID(p.id.row, p.id.column - 1))
                if (p.joinedEdges.right) add(PuzzlePieceID(p.id.row, p.id.column + 1))
            }
            for (n in candidates) if (visited.add(n)) queue.add(n)
        }
        return visited
    }

    private fun saveSnapshot() {
        val eng = _engine.value ?: return
        val snap = SessionSnapshot(
            levelId = level.id,
            timeLimit = level.timeLimit,
            pieces = eng.pieces.map {
                SessionSnapshot.PiecePos(it.id.row, it.id.column, it.position.x, it.position.y, it.zIndex)
            },
            moveCount = eng.moveCount,
            elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0
        )
        sessionStore?.save(snap)
    }

    /** Called when Android backgrounds the activity, before the process may be reclaimed. */
    fun saveNow() {
        if (_engine.value != null && !_complete.value && !_outOfTime.value) saveSnapshot()
    }

    val elapsed: Double get() = elapsedSec

    fun dispose() {
        saveNow()
        timerJob?.cancel()
        coroutineContext.cancel()
    }
}
