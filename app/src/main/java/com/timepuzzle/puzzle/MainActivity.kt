package com.timepuzzle.puzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timepuzzle.puzzle.ui.screens.LevelMapScreen
import com.timepuzzle.puzzle.ui.screens.PuzzleGameScreen
import com.timepuzzle.puzzle.ui.screens.SettingsScreen
import com.timepuzzle.puzzle.ui.theme.Cream
import com.timepuzzle.puzzle.viewmodel.GameAppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PuzzleApp() }
    }
}

@Composable
fun PuzzleApp() {
    val nav: NavHostController = rememberNavController()
    // Activity 级共享 ViewModel：地图、关卡、设置三处共用同一份进度，
    // 避免不同 NavBackStackEntry 各持一份旧数据导致「退回地图关卡又锁上」。
    val appVm: GameAppViewModel = viewModel()
    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        MaterialTheme {
            NavHost(nav, startDestination = "map") {
                composable("map") {
                    LevelMapScreen(nav, appVm, onSettings = { nav.navigate("settings") })
                }
                composable("game/{id}") { back ->
                    val id = back.arguments?.getString("id")?.toIntOrNull() ?: 1
                    PuzzleGameScreen(nav, appVm, id, onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(nav, appVm)
                }
            }
        }
    }
}
