package com.timepuzzle.puzzle.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.timepuzzle.puzzle.model.LevelDefinition
import com.timepuzzle.puzzle.model.PuzzlePieceID
import com.timepuzzle.puzzle.ui.theme.TilePlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

fun loadFullBitmap(context: Context, name: String): ImageBitmap? {
    val bmp = try { BitmapFactory.decodeStream(context.assets.open("$name.png")) } catch (_: Exception) { null }
        ?: try { BitmapFactory.decodeStream(context.assets.open("$name.jpg")) } catch (_: Exception) { null }
    return bmp?.asImageBitmap()
}

/**
 * 选关页专用缩略图：后台按目标尺寸采样解码，不触碰游戏内使用的高清原图。
 */
private object LevelThumbnailCache {
    private const val MAX_BYTES = 8 * 1024 * 1024

    val bitmaps = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
}

suspend fun loadThumbnailBitmap(
    context: Context,
    name: String,
    targetPx: Int = 320
): ImageBitmap? = withContext(Dispatchers.IO) {
    LevelThumbnailCache.bitmaps.get(name)?.asImageBitmap()
        ?: decodeSampledAsset(context, name, targetPx)?.also { bitmap ->
            LevelThumbnailCache.bitmaps.put(name, bitmap)
        }?.asImageBitmap()
}

private fun decodeSampledAsset(context: Context, name: String, targetPx: Int): Bitmap? {
    val assetName = sequenceOf("$name.png", "$name.jpg")
        .firstOrNull { candidate ->
            runCatching { context.assets.open(candidate).close() }.isSuccess
        } ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(assetName).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.assets.open(assetName).use { BitmapFactory.decodeStream(it, null, options) }
}

private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var sampleSize = 1
    val largestEdge = maxOf(width, height)
    while (largestEdge / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }
    return sampleSize
}

fun loadTileBitmaps(context: Context, level: LevelDefinition): Map<PuzzlePieceID, ImageBitmap> {
    val bmp = try { BitmapFactory.decodeStream(context.assets.open("${level.image}.png")) } catch (_: Exception) { null }
        ?: try { BitmapFactory.decodeStream(context.assets.open("${level.image}.jpg")) } catch (_: Exception) { null }
        ?: return emptyMap()
    val tileW = bmp.width / level.columns
    val tileH = bmp.height / level.rows
    if (tileW <= 0 || tileH <= 0) return emptyMap()
    val result = mutableMapOf<PuzzlePieceID, ImageBitmap>()
    for (r in 0 until level.rows) {
        for (c in 0 until level.columns) {
            val sub = Bitmap.createBitmap(bmp, c * tileW, r * tileH, tileW, tileH)
            result[PuzzlePieceID(r, c)] = sub.asImageBitmap()
        }
    }
    return result
}

fun placeholderColor(id: PuzzlePieceID): androidx.compose.ui.graphics.Color {
    val index = abs((id.row * 31 + id.column * 17)) % TilePlaceholder.size
    return TilePlaceholder[index]
}
