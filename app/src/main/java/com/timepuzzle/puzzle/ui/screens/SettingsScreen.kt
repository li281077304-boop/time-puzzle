package com.timepuzzle.puzzle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.timepuzzle.puzzle.ui.theme.Brown
import com.timepuzzle.puzzle.ui.theme.BrownDark
import com.timepuzzle.puzzle.ui.theme.Cream
import com.timepuzzle.puzzle.ui.theme.CreamLight
import com.timepuzzle.puzzle.viewmodel.GameAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, appVm: GameAppViewModel) {
    val settings = appVm.progress.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = Brown) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Brown)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamLight)
            )
        },
        containerColor = Cream
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("声音与反馈", style = MaterialTheme.typography.titleMedium, color = BrownDark)
            SettingRow("背景音乐") {
                Switch(checked = settings.musicEnabled, onCheckedChange = { v -> appVm.updateSettings { it.copy(musicEnabled = v) } })
            }
            SettingRow("音效") {
                Switch(checked = settings.soundEnabled, onCheckedChange = { v -> appVm.updateSettings { it.copy(soundEnabled = v) } })
            }
            SettingRow("震动") {
                Switch(checked = settings.hapticsEnabled, onCheckedChange = { v -> appVm.updateSettings { it.copy(hapticsEnabled = v) } })
            }

            Spacer(Modifier.height(20.dp))
            Text("开发", style = MaterialTheme.typography.titleMedium, color = BrownDark)
            SettingRow("无限体力") {
                Switch(checked = settings.unlimitedEnergy, onCheckedChange = { v -> appVm.updateSettings { it.copy(unlimitedEnergy = v) } })
            }
            Button(onClick = { appVm.resetProgress(); nav.popBackStack() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(top = 12.dp)) {
                Text("清空全部存档", color = MaterialTheme.colorScheme.onErrorContainer)
            }

            Spacer(Modifier.height(20.dp))
            Text("关于", style = MaterialTheme.typography.titleMedium, color = BrownDark)
            Text("时光拼图\n完全离线 · 无账号 · 无广告 · 无网络请求", color = BrownDark)
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = BrownDark, modifier = Modifier.weight(1f))
        control()
    }
}
