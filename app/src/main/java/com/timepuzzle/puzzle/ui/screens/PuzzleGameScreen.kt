package com.timepuzzle.puzzle.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.timepuzzle.puzzle.audio.SoundManager
import com.timepuzzle.puzzle.ui.loadFullBitmap
import com.timepuzzle.puzzle.ui.loadTileBitmaps
import com.timepuzzle.puzzle.ui.theme.*
import com.timepuzzle.puzzle.viewmodel.GameAppViewModel
import com.timepuzzle.puzzle.viewmodel.GameSession
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleGameScreen(nav: NavHostController, appVm: GameAppViewModel, levelId: Int, onBack: () -> Unit) {
    val level = appVm.levels.firstOrNull { it.id == levelId }
    if (level == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val sessionStore = remember { com.timepuzzle.puzzle.data.SessionStore(context) }
    DisposableEffect(Unit) { onDispose { soundManager.release() } }

    val session = remember(levelId) { GameSession(level, soundManager, sessionStore) }
    DisposableEffect(Unit) { onDispose { session.dispose() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tileBitmaps = remember(level.image) { loadTileBitmaps(context, level) }
    val ghost = remember(level.image) { loadFullBitmap(context, level.image) }

    var showPreview by remember { mutableStateOf(true) }
    var showOriginal by remember { mutableStateOf(false) }
    var showTimeout by remember { mutableStateOf(false) }
    var hintFlash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1500); showPreview = false }
    LaunchedEffect(session.isOutOfTime) { if (session.isOutOfTime) showTimeout = true }
    LaunchedEffect(hintFlash) {
        if (hintFlash) {
            delay(1200)
            hintFlash = false
        }
    }
    LaunchedEffect(session.isComplete) { soundManager.playComplete() }
    // 通关立即记录并落盘（不等 2.5 秒自动跳转，避免中途大退丢通关记录）
    LaunchedEffect(session.isComplete) {
        if (session.isComplete) {
            appVm.finish(level, session.elapsed, session.engine?.moveCount ?: 0)
        }
    }

    Box(Modifier.fillMaxSize().background(Cream)) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "地图", tint = Brown) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("第 ${level.id} 关", fontWeight = FontWeight.Bold, color = Brown)
                    Text(level.title, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                Text("🪙 ${level.rewardCoins}", color = Brown)
            }

            Spacer(Modifier.height(10.dp))

            // 游戏内容区（预览图/拼图板+计时+按钮）整体垂直居中
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            if (showPreview) {
                // 预览：在剩余空间内垂直居中
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("记住这张老照片", style = MaterialTheme.typography.headlineSmall, color = BrownDark)
                    Spacer(Modifier.height(16.dp))
                    val ph = TilePlaceholder[level.id % TilePlaceholder.size]
                    val pw = (ghost?.width ?: 1).toFloat()
                    val phh = (ghost?.height ?: 1).toFloat()
                    val pAspect = pw / phh
                    if (ghost != null) {
                        Image(ghost, null, Modifier.fillMaxWidth(0.8f).aspectRatio(pAspect).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit)
                    } else {
                        Box(Modifier.fillMaxWidth(0.8f).aspectRatio(pAspect).background(ph).clip(RoundedCornerShape(14.dp)))
                    }
                }
                }
            } else {
                // 拼图板占满剩余空间，内部按图片宽高比缩放并居中（不失真）
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PuzzleBoard(
                    session = session,
                    tileBitmaps = tileBitmaps,
                    ghost = ghost,
                    modifier = Modifier.fillMaxSize(),
                    hintFlash = hintFlash
                )
                }
                Spacer(Modifier.height(10.dp))
                val secs = session.remainingSeconds
                val timeText = String.format("%02d:%02d", secs / 60, secs % 60)
                Text("剩余 $timeText · 跨过相邻格中心即可换位",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (secs <= 30) Color(0xFFC62828) else Brown,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
                // 底栏三按钮（圆形绿色同风格）：提示 / 原图 / 磁铁
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                ) {
                    RoundIconButton(
                        icon = Icons.Default.Lightbulb,
                        label = "提示",
                        enabled = !session.isComplete,
                        onClick = { hintFlash = true }
                    )
                    RoundIconButton(
                        icon = Icons.Default.Image,
                        label = "原图",
                        enabled = !session.isComplete,
                        onClick = { showOriginal = true }
                    )
                    RoundIconButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "磁铁",
                        badge = if (appVm.availableHints > 0) "${appVm.availableHints}" else null,
                        enabled = appVm.availableHints > 0 && !session.isComplete,
                        onClick = {
                            if (appVm.consumeHint()) session.applyHint()
                        }
                    )
                }
            }
                }
            }
        }

        // 完成弹窗：图在上 / logo 在下 / 按钮在底部，整体居中靠下，不挡图
        if (session.isComplete) {
            val next = appVm.nextLevel(level)
            LaunchedEffect(next?.id) {
                if (next != null) {
                    delay(2500)
                    appVm.finish(level, session.elapsed, session.engine?.moveCount ?: 0)
                    appVm.start(next)
                    nav.navigate("game/${next.id}") {
                        popUpTo("game/${level.id}") { inclusive = true }
                    }
                }
            }
            // 完成图按图片真实宽高比显示（不裁成方块、不失真）
            val imgW = (ghost?.width ?: 1).toFloat()
            val imgH = (ghost?.height ?: 1).toFloat()
            val imgAspect = imgW / imgH
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(Modifier.weight(0.18f))
                    // 上半部分：完成图（用户欣赏一下）
                    Box(
                        Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(imgAspect)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                    ) {
                        if (ghost != null) {
                            Image(ghost, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 下半部分：logo + 数据 + 按钮（位置靠下，不遮图）
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✦ 恭喜完成 ✦", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFE8A1))
                        val secs = session.remainingSeconds
                        Text("剩余 ${String.format("%02d:%02d", secs / 60, secs % 60)} · 获得 ${level.rewardCoins} 金币",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            appVm.finish(level, session.elapsed, session.engine?.moveCount ?: 0)
                            onBack()
                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Text("回到地图")
                        }
                        if (next != null) {
                            Button(onClick = {
                                appVm.finish(level, session.elapsed, session.engine?.moveCount ?: 0)
                                appVm.start(next)
                                nav.navigate("game/${next.id}") {
                                    popUpTo("game/${level.id}") { inclusive = true }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE08A00))) {
                                Text("下一关")
                            }
                        }
                    }
                    Spacer(Modifier.weight(0.25f))
                }
            }
        }

        if (showTimeout) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.32f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)
                    .background(CreamLight, RoundedCornerShape(20.dp)).padding(28.dp)) {
                    Text("时间到了", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brown)
                    Text("这局没能拼完，再试一次吧", color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = onBack) { Text("回到地图", color = Brown) }
                        Button(onClick = { showTimeout = false; session.restart() },
                            colors = ButtonDefaults.buttonColors(containerColor = Brown)) { Text("重试") }
                    }
                }
            }
        }
    }

    if (showOriginal) {
        AlertDialog(onDismissRequest = { showOriginal = false }, confirmButton = {
            TextButton(onClick = { showOriginal = false }) { Text("收起") }
        }, text = {
            if (ghost != null) {
                Image(ghost, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(TilePlaceholder[level.id % TilePlaceholder.size]))
            }
        })
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val color = if (enabled) GreenPrimary else GreenPrimary.copy(alpha = 0.4f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (badge != null) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Brown)
    }
}
