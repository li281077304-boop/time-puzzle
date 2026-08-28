package com.timepuzzle.puzzle.engine

import com.timepuzzle.puzzle.model.*

data class PuzzleMoveResult(
    val pieceID: PuzzlePieceID,
    val didSnap: Boolean,
    val didCompleteLevel: Boolean
)

class PuzzleEngine(
    val grid: PuzzleGrid,
    var boardSize: PuzzleSize,
    var snapDistance: Double = 18.0,
    private val shapeStrategy: PuzzleShapeStrategy = RectangleShapeStrategy()
) {
    var pieces: List<PuzzlePieceState> = emptyList()
        private set
    var moveCount = 0
        private set
    var activePieceID: PuzzlePieceID? = null
        private set

    private var dragStartPosition: PuzzlePoint? = null
    private var dragStartPositions: Map<PuzzlePieceID, PuzzlePoint> = emptyMap()
    private var activeGroupIDs: Set<PuzzlePieceID> = emptySet()
    private var highestZIndex: Int = 0
    /** 已形成的连接对集合：粘性保留，拖动连接组不会散架。必须放在 init 之前。 */
    private var stickyJoins: MutableSet<PuzzleJoinPair> = mutableSetOf()

    init {
        require(boardSize.width > 0 && boardSize.height > 0) { "invalid board size" }
        snapDistance = maxOf(0.0, snapDistance)
        highestZIndex = grid.pieceCount
        pieces = makePieces()
        recomputeJoinedEdges()
    }

    /** 所有拼块都停在各自的正确位置即算过关（拼块本身不锁定，随时可再移动）。 */
    val isCompleted: Boolean get() = pieces.all { it.position.distance(it.homePosition) <= 0.5 }

    fun piece(id: PuzzlePieceID): PuzzlePieceState? = pieces.firstOrNull { it.id == id }

    /** Sattolo's algorithm: single n-cycle permutation, zero fixed points. */
    fun shuffle(seed: Long? = null) {
        if (pieces.size <= 1) return
        val order = pieces.indices.toMutableList()
        val rng = if (seed != null) kotlin.random.Random(seed) else kotlin.random.Random
        for (i in (order.size - 1) downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
        val newPieces = pieces.toMutableList()
        for ((posIndex, pieceIndex) in order.withIndex()) {
            newPieces[pieceIndex] = newPieces[pieceIndex].copy(
                position = pieces[posIndex].homePosition,
                isLocked = false,
                zIndex = pieceIndex,
                joinedEdges = PuzzleJoinedEdges()
            )
        }
        pieces = newPieces
        highestZIndex = maxOf(1, pieces.size)
        stickyJoins.clear()
        activePieceID = null
        dragStartPosition = null
        dragStartPositions = emptyMap()
        activeGroupIDs = emptySet()
        moveCount = 0
        recomputeJoinedEdges()
    }

    /** 从中途存档恢复：按图块 id 还原位置/zIndex，并重算连接。 */
    fun restorePositions(
        positions: Map<PuzzlePieceID, PuzzlePoint>,
        zIndexes: Map<PuzzlePieceID, Int>,
        moveCount: Int
    ) {
        pieces.forEach { p ->
            positions[p.id]?.let { p.position = it }
            zIndexes[p.id]?.let { p.zIndex = it }
        }
        this.moveCount = moveCount
        activePieceID = null
        dragStartPosition = null
        dragStartPositions = emptyMap()
        activeGroupIDs = emptySet()
        highestZIndex = maxOf(1, pieces.size, zIndexes.values.maxOrNull() ?: 0)
        stickyJoins.clear() // 中途恢复时不带粘性连接，让 recompute 重新算
        recomputeJoinedEdges()
    }

    fun beginDrag(pieceID: PuzzlePieceID): Set<PuzzlePieceID> {
        val index = pieces.indexOfFirst { it.id == pieceID }
        require(index >= 0) { "unknown piece $pieceID" }
        require(!pieces[index].isLocked) { "locked piece $pieceID" }
        highestZIndex += 1
        pieces[index].zIndex = highestZIndex
        activePieceID = pieceID
        dragStartPosition = pieces[index].position
        activeGroupIDs = connectedGroup(pieceID)
        dragStartPositions = pieces.filter { activeGroupIDs.contains(it.id) }
            .associate { it.id to it.position }
        activeGroupIDs.forEach { gid ->
            highestZIndex += 1
            pieces.first { it.id == gid }.zIndex = highestZIndex
        }
        return activeGroupIDs
    }

    fun updateActiveDrag(translation: PuzzlePoint) {
        require(activePieceID != null && dragStartPosition != null) { "no active drag" }
        pieces.forEach { p ->
            if (activeGroupIDs.contains(p.id)) {
                val start = dragStartPositions[p.id] ?: return@forEach
                p.position = start + translation
            }
        }
    }

    fun endActiveDrag(forceSnap: Boolean = false, finalTranslation: PuzzlePoint? = null): PuzzleMoveResult {
        if (finalTranslation != null) updateActiveDrag(finalTranslation)
        val activeID = activePieceID ?: throw IllegalStateException("no active drag")
        val index = pieces.indexOfFirst { it.id == activeID }
        val joinsBefore = joinedPairs()
        val originalDragStarts = dragStartPositions
        val snapTarget = if (forceSnap) pieces[index].homePosition
        else nearestSlotAlways(pieces[index].position)
        var didSnap = false
        if (snapTarget != null) {
            didSnap = placeActiveGroup(anchorTarget = snapTarget, anchorID = activeID)
            if (!didSnap) {
                dragStartPositions.forEach { (id, start) ->
                    if (id != activeID) pieces.firstOrNull { it.id == id }?.position = start
                }
                activeGroupIDs = setOf(activeID)
                dragStartPositions = mapOf(activeID to (dragStartPosition ?: pieces[index].position))
                didSnap = placeActiveGroup(anchorTarget = snapTarget, anchorID = activeID)
            }
        }
        if (!didSnap) {
            originalDragStarts.forEach { (id, start) ->
                pieces.firstOrNull { it.id == id }?.position = start
            }
        }
        recomputeJoinedEdges()
        moveCount += 1
        activePieceID = null
        dragStartPosition = null
        dragStartPositions = emptyMap()
        activeGroupIDs = emptySet()
        return PuzzleMoveResult(activeID, didSnap, isCompleted)
    }

    private val slotOccupancyDistance: Double
        get() = minOf(boardSize.width / grid.columns, boardSize.height / grid.rows) * 0.18

    fun shouldSnap(piece: PuzzlePieceState): Boolean = !piece.isLocked && nearestSlot(piece.position) != null

    private fun nearestSlot(position: PuzzlePoint): PuzzlePoint? {
        val nearest = pieces.minByOrNull { it.homePosition.distance(position) } ?: return null
        return if (nearest.homePosition.distance(position) <= snapDistance) nearest.homePosition else null
    }

    private fun nearestSlotAlways(position: PuzzlePoint): PuzzlePoint? =
        pieces.minByOrNull { it.homePosition.distance(position) }?.homePosition

    private fun placeActiveGroup(anchorTarget: PuzzlePoint, anchorID: PuzzlePieceID): Boolean {
        val anchorStart = dragStartPositions[anchorID] ?: return false
        val targetSlots = activeGroupIDs.mapNotNull { id ->
            val start = dragStartPositions[id] ?: return@mapNotNull null
            val candidate = PuzzlePoint(
                x = anchorTarget.x + (start.x - anchorStart.x),
                y = anchorTarget.y + (start.y - anchorStart.y)
            )
            // 放宽：最近槽位（snapDistance 内）就接受，确保连接组拖动不卡住
            nearestSlot(candidate)?.let { id to it }
        }.toMap()
        if (targetSlots.size != activeGroupIDs.size) return false
        if (targetSlots.values.toSet().size != activeGroupIDs.size) return false
        // 目标槽位上被组外图块占据的（阻挡块）
        val externalBlockers = pieces.filter { p ->
            !activeGroupIDs.contains(p.id) && targetSlots.values.contains(p.position)
        }
        if (externalBlockers.any { it.isLocked }) return false
        val formerSet = dragStartPositions.values.toSet()
        val vacated = formerSet.subtract(targetSlots.values.toSet())
        // 允许两类落位：
        // 1) 目标全空 → 直接移动（不再因为"没有可交换的块"而拖不动）
        // 2) 目标被占 → 阻挡块少于等于腾出的槽位数时，逐个换到空位
        if (externalBlockers.size > vacated.size) return false
        externalBlockers.sortedBy { it.id }.zip(vacated.sortedBy { it.x + it.y }).forEach { (blocker, vacancy) ->
            pieces.first { it.id == blocker.id }.position = vacancy
        }
        targetSlots.forEach { (id, slot) ->
            val piece = pieces.first { it.id == id }
            piece.position = slot
            // 不锁定：即使停在正确位置，拼块也仍然可以继续被拖动
            piece.isLocked = false
        }
        return true
    }

    private fun exactSlot(near: PuzzlePoint): PuzzlePoint? {
        val slot = nearestSlotAlways(near) ?: return null
        return if (slot.distance(near) <= slotOccupancyDistance) slot else null
    }

    private fun connectedGroup(containing: PuzzlePieceID): Set<PuzzlePieceID> {
        val visited = mutableSetOf(containing)
        val pending = mutableListOf(containing)
        while (pending.isNotEmpty()) {
            val current = pending.removeAt(pending.lastIndex)
            val currentPiece = piece(current) ?: continue
            val adjacent = pieces.mapNotNull { candidate ->
                val joined = (currentPiece.joinedEdges.top && candidate.id.row == currentPiece.id.row - 1 && candidate.id.column == currentPiece.id.column) ||
                    (currentPiece.joinedEdges.right && candidate.id.row == currentPiece.id.row && candidate.id.column == currentPiece.id.column + 1) ||
                    (currentPiece.joinedEdges.bottom && candidate.id.row == currentPiece.id.row + 1 && candidate.id.column == currentPiece.id.column) ||
                    (currentPiece.joinedEdges.left && candidate.id.row == currentPiece.id.row && candidate.id.column == currentPiece.id.column - 1)
                if (joined) candidate.id else null
            }
            for (next in adjacent) if (visited.add(next)) pending.add(next)
        }
        return visited
    }

    private data class JoinCandidate(val pair: PuzzleJoinPair, val firstIndex: Int, val secondIndex: Int, val firstEdge: JoinedSide, val secondEdge: JoinedSide)
    private class PuzzleJoinPair(a: PuzzlePieceID, b: PuzzlePieceID) {
        val first: PuzzlePieceID
        val second: PuzzlePieceID
        init {
            if (a < b) { first = a; second = b } else { first = b; second = a }
        }
        override fun equals(other: Any?): Boolean =
            other is PuzzleJoinPair && first == other.first && second == other.second
        override fun hashCode(): Int = 31 * first.hashCode() + second.hashCode()
    }
    private enum class JoinedSide { TOP, RIGHT, BOTTOM, LEFT }

    private fun joinedPairs(): Set<PuzzleJoinPair> {
        val result = mutableSetOf<PuzzleJoinPair>()
        for (p in pieces) {
            if (p.joinedEdges.right) result.add(PuzzleJoinPair(p.id, PuzzlePieceID(p.id.row, p.id.column + 1)))
            if (p.joinedEdges.bottom) result.add(PuzzleJoinPair(p.id, PuzzlePieceID(p.id.row + 1, p.id.column)))
        }
        return result
    }

    /** 单块直接放到正确位置（备用，磁铁改为用 placeCluster 不剧透答案）。 */
    fun forcePlaceHome(id: PuzzlePieceID) {
        val p = pieces.firstOrNull { it.id == id } ?: return
        p.position = p.homePosition
        p.zIndex = ++highestZIndex
        moveCount += 1
        recomputeJoinedEdges()
    }

    /**
     * 磁铁用：把给定的图块吸成一坨（吸附在一起），但**不放到正确位置**——
     * 找一块连续的空白区域把它们排成一排，让它们连成一组，方便玩家下一步拼。
     */
    fun placeCluster(ids: List<PuzzlePieceID>) {
        val clusterIds = ids.toSet()
        if (clusterIds.size < 2) return
        val n = clusterIds.size
        val cols = grid.columns
        val rows = grid.rows
        val slotW = boardSize.width / cols
        val slotH = boardSize.height / rows
        val others = pieces.filter { it.id !in clusterIds }

        // 候选锚点 = 水平或垂直 n 连的所有起点，随机顺序
        val anchors = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) for (c in 0..cols - n) anchors.add(r to c) // 水平
        for (r in 0..rows - n) for (c in 0 until cols) anchors.add(r to c) // 垂直
        anchors.shuffle()

        for ((r0, c0) in anchors) {
            val horizontal = c0 <= cols - n
            val targets = (0 until n).map { i ->
                val (dr, dc) = if (horizontal) Pair(0, i) else Pair(i, 0)
                PuzzlePoint((c0 + dc) * slotW + slotW / 2, (r0 + dr) * slotH + slotH / 2)
            }
            val conflict = others.any { p -> targets.any { it.distance(p.position) < 1.0 } }
            if (conflict) continue
            clusterIds.forEachIndexed { i, id ->
                val p = piece(id) ?: return@forEachIndexed
                p.position = targets[i]
                highestZIndex += 1
                p.zIndex = highestZIndex
            }
            moveCount += 1
            recomputeJoinedEdges()
            return
        }
        // 找不到完全空白的位置，跳过
    }

    private fun recomputeJoinedEdges(preserving: Set<PuzzleJoinPair>? = null, newJoinLimit: Int? = null) {
        pieces.forEach { it.joinedEdges = PuzzleJoinedEdges() }
        // 连接判定用严格容差（约 1% 格子宽，最小 2px）
        val cell = minOf(boardSize.width / grid.columns, boardSize.height / grid.rows)
        val tolerance = maxOf(2.0, cell * 0.01)
        val candidates = mutableListOf<JoinCandidate>()
        for (li in pieces.indices) {
            for (ri in pieces.indices) {
                if (ri <= li) continue
                val lhs = pieces[li]; val rhs = pieces[ri]
                val dx = rhs.position.x - lhs.position.x
                val dy = rhs.position.y - lhs.position.y
                val tw = lhs.homeFrame.size.width
                val th = lhs.homeFrame.size.height
                when {
                    lhs.id.row == rhs.id.row && lhs.id.column + 1 == rhs.id.column &&
                        kotlin.math.abs(dx - tw) <= tolerance && kotlin.math.abs(dy) <= tolerance ->
                        candidates.add(JoinCandidate(PuzzleJoinPair(lhs.id, rhs.id), li, ri, JoinedSide.RIGHT, JoinedSide.LEFT))
                    rhs.id.row == lhs.id.row && rhs.id.column + 1 == lhs.id.column &&
                        kotlin.math.abs(dx + tw) <= tolerance && kotlin.math.abs(dy) <= tolerance ->
                        candidates.add(JoinCandidate(PuzzleJoinPair(lhs.id, rhs.id), li, ri, JoinedSide.LEFT, JoinedSide.RIGHT))
                    lhs.id.column == rhs.id.column && lhs.id.row + 1 == rhs.id.row &&
                        kotlin.math.abs(dy - th) <= tolerance && kotlin.math.abs(dx) <= tolerance ->
                        candidates.add(JoinCandidate(PuzzleJoinPair(lhs.id, rhs.id), li, ri, JoinedSide.BOTTOM, JoinedSide.TOP))
                    rhs.id.column == lhs.id.column && rhs.id.row + 1 == lhs.id.row &&
                        kotlin.math.abs(dy + th) <= tolerance && kotlin.math.abs(dx) <= tolerance ->
                        candidates.add(JoinCandidate(PuzzleJoinPair(lhs.id, rhs.id), li, ri, JoinedSide.TOP, JoinedSide.BOTTOM))
                }
            }
        }
        // 粘性连接：已经连接过的图块对，无论现在位置是否还在容差内，都保留连接
        val stickyThatAreCandidates = candidates.filter { stickyJoins.contains(it.pair) }
        // 除此之外的新连接：去掉 preserving（默认），允许的则加入
        val newOnes = candidates
            .filter { !stickyJoins.contains(it.pair) && !(preserving?.contains(it.pair) ?: false) }
        val admitted = newJoinLimit?.let { newOnes.take(it) } ?: newOnes
        val toApply = (stickyThatAreCandidates + admitted).distinctBy { it.pair }
        for (c in toApply) {
            setEdge(c.firstIndex, c.firstEdge)
            setEdge(c.secondIndex, c.secondEdge)
        }
        // 维护 stickyJoins：保留所有已应用的连接（粘性不变），新增 admitted
        stickyJoins = (stickyJoins + admitted.map { it.pair }).toMutableSet()
    }

    /** 重置粘性连接（用在 shuffle / restart / restorePositions）。 */
    private fun resetStickyJoins() {
        stickyJoins.clear()
    }

    private fun setEdge(index: Int, edge: JoinedSide) {
        when (edge) {
            JoinedSide.TOP -> pieces[index].joinedEdges.top = true
            JoinedSide.RIGHT -> pieces[index].joinedEdges.right = true
            JoinedSide.BOTTOM -> pieces[index].joinedEdges.bottom = true
            JoinedSide.LEFT -> pieces[index].joinedEdges.left = true
        }
    }

    private fun makePieces(): List<PuzzlePieceState> {
        val pieceW = boardSize.width / grid.columns
        val pieceH = boardSize.height / grid.rows
        return (0 until grid.rows).flatMap { row ->
            (0 until grid.columns).map { column ->
                val id = PuzzlePieceID(row, column)
                val frame = PuzzleRect(
                    origin = PuzzlePoint(column * pieceW, row * pieceH),
                    size = PuzzleSize(pieceW, pieceH)
                )
                PuzzlePieceState(
                    id = id,
                    homeFrame = frame,
                    position = frame.center,
                    zIndex = row * grid.columns + column
                )
            }
        }
    }
}
