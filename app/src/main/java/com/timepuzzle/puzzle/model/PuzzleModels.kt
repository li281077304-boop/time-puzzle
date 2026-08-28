package com.timepuzzle.puzzle.model

import kotlin.math.hypot

data class PuzzleSize(val width: Double, val height: Double)
data class PuzzlePoint(val x: Double, val y: Double) {
    operator fun plus(o: PuzzlePoint) = PuzzlePoint(x + o.x, y + o.y)
    operator fun minus(o: PuzzlePoint) = PuzzlePoint(x - o.x, y - o.y)
    fun distance(to: PuzzlePoint): Double = hypot(x - to.x, y - to.y)
}

data class PuzzleRect(val origin: PuzzlePoint, val size: PuzzleSize) {
    val center: PuzzlePoint get() = PuzzlePoint(origin.x + size.width / 2, origin.y + size.height / 2)
}

data class PuzzleGrid(val rows: Int, val columns: Int) {
    val pieceCount: Int get() = rows * columns
}

data class PuzzlePieceID(val row: Int, val column: Int) : Comparable<PuzzlePieceID> {
    override fun compareTo(other: PuzzlePieceID): Int {
        val r = row.compareTo(other.row)
        return if (r != 0) r else column.compareTo(other.column)
    }
}

data class PuzzleJoinedEdges(
    var top: Boolean = false,
    var right: Boolean = false,
    var bottom: Boolean = false,
    var left: Boolean = false
)

data class PuzzlePieceState(
    val id: PuzzlePieceID,
    val homeFrame: PuzzleRect,
    var position: PuzzlePoint,
    var zIndex: Int,
    var isLocked: Boolean = false,
    var joinedEdges: PuzzleJoinedEdges = PuzzleJoinedEdges()
) {
    val homePosition: PuzzlePoint get() = homeFrame.center
}

interface PuzzleShapeStrategy {
    fun shape(id: PuzzlePieceID, grid: PuzzleGrid): String = "rect"
}

class RectangleShapeStrategy : PuzzleShapeStrategy
