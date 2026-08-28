package com.timepuzzle.puzzle.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.timepuzzle.puzzle.model.LevelDefinition
import com.timepuzzle.puzzle.model.PuzzlePieceID
import com.timepuzzle.puzzle.ui.theme.TilePlaceholder
import kotlin.math.abs

fun loadFullBitmap(context: Context, name: String): ImageBitmap? {
    val bmp = try { BitmapFactory.decodeStream(context.assets.open("$name.png")) } catch (_: Exception) { null }
        ?: try { BitmapFactory.decodeStream(context.assets.open("$name.jpg")) } catch (_: Exception) { null }
    return bmp?.asImageBitmap()
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
