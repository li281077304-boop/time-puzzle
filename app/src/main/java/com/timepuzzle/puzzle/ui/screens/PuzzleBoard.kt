package com.timepuzzle.puzzle.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.timepuzzle.puzzle.model.PuzzlePieceID
import com.timepuzzle.puzzle.model.PuzzlePoint
import com.timepuzzle.puzzle.ui.placeholderColor
import com.timepuzzle.puzzle.ui.theme.BoardBack
import com.timepuzzle.puzzle.viewmodel.GameSession

@Composable
fun PuzzleBoard(
    session: GameSession,
    tileBitmaps: Map<PuzzlePieceID, ImageBitmap>,
    ghost: ImageBitmap?,
    modifier: Modifier = Modifier,
    hintFlash: Boolean = false
) {
    val density = LocalDensity.current
    // 占用父级给的整个可用区域，再按图片宽高比算出棋盘尺寸 → 图片不会失真
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()
        val imgW = (ghost?.width ?: 1).toFloat()
        val imgH = (ghost?.height ?: 1).toFloat()
        val imgAspect = imgW / imgH

        // 在可用区域内取最大的、保持图片比例的内接矩形
        val boardW: Float
        val boardH: Float
        if (availW / availH > imgAspect) {
            boardH = availH
            boardW = availH * imgAspect
        } else {
            boardW = availW
            boardH = availW / imgAspect
        }
        if (boardW <= 0 || boardH <= 0) return@BoxWithConstraints

        LaunchedEffect(boardW, boardH) {
            if (boardW > 0 && boardH > 0) session.configure(boardW.toDouble(), boardH.toDouble())
        }

        val engine = session.engine ?: run { Box(Modifier.fillMaxSize()); return@BoxWithConstraints }
        val version = session.version
        val newlyHomeIDs = session.newlyHomeIDs
        val newlyJoinedIDs = session.newlyJoinedIDs

        val tileW = boardW / engine.grid.columns
        val tileH = boardH / engine.grid.rows
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var draggingIDs by remember { mutableStateOf<Set<PuzzlePieceID>>(emptySet()) }

        Box(
            Modifier
                .size((boardW / density.density).dp, (boardH / density.density).dp)
                .align(Alignment.Center)
                .background(BoardBack)
                .clip(RoundedCornerShape(18.dp))
        ) {
            // 不再放常驻半透原图——会剧透答案。点「提示」按钮时临时全亮显示。
            // 提示高亮层（点「提示」时临时叠加在图块之上，把整张原图显示出来）
            if (hintFlash && ghost != null) {
                Image(
                    ghost, null,
                    modifier = Modifier.fillMaxSize().alpha(0.6f),
                    contentScale = ContentScale.FillBounds
                )
            }
            // 按 zIndex 排序渲染：拖拽中的图块 zIndex 最高，保证永远在最上层
            val sortedPieces = engine.pieces.sortedBy { it.zIndex }
            sortedPieces.forEach { piece ->
                // key(piece.id) 保证稳定身份：拖拽开始时 zIndex 重排不会重建节点、
                // 不会中断正在进行的拖拽手势（否则手感变差）
                key(piece.id) {
                    val dragging = draggingIDs.contains(piece.id)
                    val ox = (piece.position.x - tileW / 2 + if (dragging) dragOffset.x else 0f)
                    val oy = (piece.position.y - tileH / 2 + if (dragging) dragOffset.y else 0f)
                    val isAtHome = piece.position.distance(piece.homePosition) <= 0.5
                    // 连接边必须以双方任一边的连接状态为准。中途恢复或
                    // 拖动时，另一块已经标记连接而这一块尚未重组的短暂帧
                    // 也不能画出白色内缝。
                    val joinedTop = joinedOnEitherSide(engine, piece, Side.TOP)
                    val joinedRight = joinedOnEitherSide(engine, piece, Side.RIGHT)
                    val joinedBottom = joinedOnEitherSide(engine, piece, Side.BOTTOM)
                    val joinedLeft = joinedOnEitherSide(engine, piece, Side.LEFT)
                    // 圆角裁剪：只有「两条相邻边都未连接」的外轮廓角才圆角（小圆角，
                    // 接近直角）；任意一边已连接的内部接缝角保持直角 → 拼合后图片连续
                    // 无缝、四块交汇处不露背景、不形成节点。gap=0 相邻拼块紧贴。
                    val radiusPx = with(density) { 2.dp.toPx() }
                    val tileShape = remember(joinedTop, joinedRight, joinedBottom, joinedLeft, radiusPx) {
                        GenericShape { size, _ ->
                            val r = radiusPx
                            val tl = if (!(joinedTop || joinedLeft)) r else 0f
                            val tr = if (!(joinedTop || joinedRight)) r else 0f
                            val br = if (!(joinedBottom || joinedRight)) r else 0f
                            val bl = if (!(joinedBottom || joinedLeft)) r else 0f
                            addRoundRect(
                                RoundRect(
                                    0f, 0f, size.width, size.height,
                                    topLeftCornerRadius = CornerRadius(tl),
                                    topRightCornerRadius = CornerRadius(tr),
                                    bottomRightCornerRadius = CornerRadius(br),
                                    bottomLeftCornerRadius = CornerRadius(bl)
                                )
                            )
                        }
                    }
                    Box(
                        Modifier
                            // 保留浮点位移，避免 6×7 等非整像素格宽时每块各自
                            // 四舍五入而在连接处出现 1px 白缝。
                            .graphicsLayer {
                                translationX = ox.toFloat()
                                translationY = oy.toFloat()
                            }
                            .size((tileW / density.density).dp, (tileH / density.density).dp)
                            // 每块本身裁成圆角（已连接的接缝角保持直角）→ 圆润且内部无空白
                            .clip(tileShape)
                            // 单线深色边框：参考图为拼块之间有一条深色分隔线，
                            // 拼块圆润；连接后共享边自动隐藏，形成无缝整体。
                            .drawWithContent {
                                drawContent()
                                // 【写死规则】拼好的图（session 完成态）必须无缝无中缝：内部接缝边不画分割线。
                                val isComplete = session.isComplete
                                // 双层细边框：黑边中心压在块边缘（半内半外）→ 相邻拼块紧贴
                                // 时两块黑线重合为「一条」细分割线；白线只在图片内部 inset 提亮
                                // 隔离。每条边都画（含已连接边）→ 拼好后仍是一条细线分块而非
                                // 无缝全图；内部接缝角直角、四块交汇无节点。
                                val R = radiusPx
                                val w = size.width
                                val h = size.height
                                val blackW = 1.25.dp.toPx()
                                val whiteW = 0.75.dp.toPx()
                                val whiteInset = 1.dp.toPx()
                                val black = Color(0xFF222222).copy(alpha = 0.85f)
                                val white = Color(0xFFFFFFFF).copy(alpha = 0.7f)
                                // i = 线中心到边缘的距离；黑边 i=0（中心在边缘），白边 i=whiteInset
                                fun drawLayer(i: Float, width: Float, color: Color) {
                                    val r = (R - i).coerceAtLeast(0f)
                                    val arc = Size(2 * r, 2 * r)
                                    // 【写死规则】外轮廓边（未连接）始终画；内部接缝边（已连接）仅在
                                    // 「拼图未完成」时画。完成态不画 → 拼好的图无缝无中缝，是一张完整图片。
                                    val drawTop = !joinedTop || !isComplete
                                    val drawBottom = !joinedBottom || !isComplete
                                    val drawLeft = !joinedLeft || !isComplete
                                    val drawRight = !joinedRight || !isComplete
                                    val xLt = if (!(joinedTop || joinedLeft)) r else 0f
                                    val xRt = if (!(joinedTop || joinedRight)) w - r else w
                                    if (drawTop) drawLine(color, Offset(xLt, i), Offset(xRt, i), strokeWidth = width)        // 顶
                                    val xLb = if (!(joinedBottom || joinedLeft)) r else 0f
                                    val xRb = if (!(joinedBottom || joinedRight)) w - r else w
                                    if (drawBottom) drawLine(color, Offset(xLb, h - i), Offset(xRb, h - i), strokeWidth = width)  // 底
                                    val yTl = if (!(joinedTop || joinedLeft)) r else 0f
                                    val yBl = if (!(joinedBottom || joinedLeft)) h - r else h
                                    if (drawLeft) drawLine(color, Offset(i, yTl), Offset(i, yBl), strokeWidth = width)        // 左
                                    val yTr = if (!(joinedTop || joinedRight)) r else 0f
                                    val yBr = if (!(joinedBottom || joinedRight)) h - r else h
                                    if (drawRight) drawLine(color, Offset(w - i, yTr), Offset(w - i, yBr), strokeWidth = width)  // 右
                                    // 四个外轮廓圆角（仅最外轮廓角画弧；半径=R-i，与裁切同心）
                                    if (!(joinedTop || joinedLeft)) drawArc(color, 180f, 90f, false, topLeft = Offset(i, i), size = arc, style = Stroke(width))
                                    if (!(joinedTop || joinedRight)) drawArc(color, 270f, 90f, false, topLeft = Offset(w - i - 2 * r, i), size = arc, style = Stroke(width))
                                    if (!(joinedBottom || joinedRight)) drawArc(color, 0f, 90f, false, topLeft = Offset(w - i - 2 * r, h - i - 2 * r), size = arc, style = Stroke(width))
                                    if (!(joinedBottom || joinedLeft)) drawArc(color, 90f, 90f, false, topLeft = Offset(i, h - i - 2 * r), size = arc, style = Stroke(width))
                                }
                                drawLayer(0f, blackW, black)          // 黑边：中心在边缘，半内半外（相邻紧贴→重合为一条细线）
                                drawLayer(whiteInset, whiteW, white) // 白线：在图片内部，仅提亮隔离
                            }
                            .pointerInput(piece.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIDs = session.begin(piece.id)
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        dragOffset += amount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        session.endDrag(PuzzlePoint(dragOffset.x.toDouble(), dragOffset.y.toDouble()))
                                        draggingIDs = emptySet()
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                    ) {
                        TileContent(
                            piece = piece,
                            bitmap = tileBitmaps[piece.id],
                            shouldFlash = newlyHomeIDs.contains(piece.id) || newlyJoinedIDs.contains(piece.id),
                            isAtHome = isAtHome
                        )
                    }
                }
            }
        }
    }
}

private enum class Side { TOP, RIGHT, BOTTOM, LEFT }

/** A shared seam is hidden when either of its two tiles reports that join. */
private fun joinedOnEitherSide(
    engine: com.timepuzzle.puzzle.engine.PuzzleEngine,
    piece: com.timepuzzle.puzzle.model.PuzzlePieceState,
    side: Side
): Boolean {
    return when (side) {
        Side.TOP -> piece.joinedEdges.top || engine.piece(PuzzlePieceID(piece.id.row - 1, piece.id.column))?.joinedEdges?.bottom == true
        Side.RIGHT -> piece.joinedEdges.right || engine.piece(PuzzlePieceID(piece.id.row, piece.id.column + 1))?.joinedEdges?.left == true
        Side.BOTTOM -> piece.joinedEdges.bottom || engine.piece(PuzzlePieceID(piece.id.row + 1, piece.id.column))?.joinedEdges?.top == true
        Side.LEFT -> piece.joinedEdges.left || engine.piece(PuzzlePieceID(piece.id.row, piece.id.column - 1))?.joinedEdges?.right == true
    }
}

@Composable
private fun TileContent(
    piece: com.timepuzzle.puzzle.model.PuzzlePieceState,
    bitmap: ImageBitmap?,
    shouldFlash: Boolean,
    isAtHome: Boolean
) {
    // 闪烁动画：放对或接续时短暂高亮（只闪本步新变化的图块）
    val flashScale = remember { Animatable(1f) }
    val flashOverlay = remember { Animatable(0f) }
    LaunchedEffect(shouldFlash) {
        if (shouldFlash) {
            flashScale.snapTo(1.04f)
            flashScale.animateTo(1f, animationSpec = tween(durationMillis = 350))
            flashOverlay.snapTo(0.55f)
            flashOverlay.animateTo(0f, animationSpec = tween(durationMillis = 500))
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(placeholderColor(piece.id))
            }
        }
        // 高光叠加层（白色短暂透出）
        Box(
            Modifier
                .fillMaxSize()
                .alpha(flashOverlay.value)
                .background(Color(0xFFFFF6CC))
        )
    }
}
