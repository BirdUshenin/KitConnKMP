package com.kitconn.shared.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitconn.shared.presentation.AppDependencies
import com.kitconn.shared.presentation.MainAction
import com.kitconn.shared.presentation.MainViewModel

/** Корневой экран, общий для Android и iOS. */
@Composable
fun KitConnApp(deps: AppDependencies) {
    val viewModel = viewModel { MainViewModel(deps) }
    val state by viewModel.uiState.collectAsState()

    KitConnTheme {
        Crossfade(
            targetState = when {
                state.isLoading -> Screen.Splash
                state.updateRequired -> Screen.Update
                else -> Screen.Main
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen()
                Screen.Update -> UpdateRequiredScreen(onRetry = { viewModel.onAction(MainAction.Refresh) })
                Screen.Main -> MainScreen(state, viewModel::onAction)
            }
        }
    }
}

private enum class Screen { Splash, Update, Main }
