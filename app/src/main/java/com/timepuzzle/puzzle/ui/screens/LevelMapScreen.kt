package com.timepuzzle.puzzle.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.timepuzzle.puzzle.model.LevelDefinition
import com.timepuzzle.puzzle.model.PuzzlePieceID
import com.timepuzzle.puzzle.ui.loadFullBitmap
import com.timepuzzle.puzzle.ui.theme.*
import com.timepuzzle.puzzle.viewmodel.GameAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelMapScreen(nav: NavHostController, appVm: GameAppViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val progress by appVm.progressState
    val groups = appVm.levels.groupBy { it.groupName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时光拼图", color = Brown) },
                actions = {
                    Text("🪙 ${progress.coins}", color = Brown, modifier = Modifier.padding(end = 4.dp))
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置", tint = Brown) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamLight)
            )
        },
        containerColor = Cream
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)
        ) {
            groups.forEach { (groupName, groupLevels) ->
                item {
                    Text(groupName, style = MaterialTheme.typography.titleMedium,
                        color = BrownDark, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(groupLevels.chunked(2)) { rowLevels ->
                    Row(Modifier.fillMaxWidth()) {
                        rowLevels.forEach { level ->
                            LevelCard(level = level, context = context, appVm = appVm, onClick = {
                                if (appVm.isUnlocked(level) && appVm.canStart()) {
                                    appVm.start(level)
                                    nav.navigate("game/${level.id}")
                                }
                            })
                        }
                        if (rowLevels.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun LevelCard(
    level: LevelDefinition,
    context: android.content.Context,
    appVm: GameAppViewModel,
    onClick: () -> Unit
) {
    val unlocked = appVm.isUnlocked(level)
    val completed = appVm.progress.completedLevels[level.id]?.isCompleted == true
    val bmp = remember(level.image) { loadFullBitmap(context, level.image) }
    val ph = TilePlaceholder[level.id % TilePlaceholder.size]

    Card(
        onClick = onClick,
        modifier = Modifier.padding(6.dp).width(150.dp).aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = CreamLight),
        border = BorderStroke(1.5.dp, Brown.copy(0.4f))
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bmp != null) {
                Image(bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.fillMaxSize().background(ph).padding(8.dp).clip(RoundedCornerShape(10.dp)))
            }
            if (!unlocked) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, "锁", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            if (completed) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(Gold, CircleShape).size(22.dp),
                    contentAlignment = Alignment.Center) {
                    Text("★", color = Color.White, fontSize = 12.sp)
                }
            }
            Text(level.title, Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brown.copy(0.7f))
                .padding(4.dp), color = Color.White, textAlign = TextAlign.Center, fontSize = 12.sp)
        }
    }
}

@Suppress("unused")
private val _ignore = PuzzlePieceID(0, 0)
