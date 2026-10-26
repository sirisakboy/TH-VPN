package com.example.h25vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.h25vpn.ui.screens.HomeScreen
import com.example.h25vpn.ui.screens.LogsScreen
import com.example.h25vpn.ui.screens.ProxyShareScreen
import com.example.h25vpn.ui.screens.ServersScreen
import com.example.h25vpn.ui.screens.SettingsScreen
import com.example.h25vpn.ui.screens.SplitTunnelScreen
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.H25VPNTheme

import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler

enum class Screen {
    HOME,
    SERVERS,
    TWEAKS,
    PROXY_SHARE,
    SPLIT_TUNNEL,
    LOGS,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            H25VPNTheme {
                MainAppNavHost()
            }
        }
    }
}

@Composable
fun MainAppNavHost() {
    val navStack = remember { mutableStateListOf(Screen.HOME) }
    val currentScreen = navStack.lastOrNull() ?: Screen.HOME

    fun navigateTo(screen: Screen) {
        navStack.add(screen)
    }

    fun navigateBack() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.lastIndex)
        }
    }

    // Handles Android 3-Button Navigation (Back triangle button) and Back Gesture
    BackHandler(enabled = navStack.size > 1) {
        navigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = DarkBg
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(
                    onNavigateToServers = { navigateTo(Screen.SERVERS) },
                    onNavigateToTweaks = { navigateTo(Screen.TWEAKS) },
                    onNavigateToProxyShare = { navigateTo(Screen.PROXY_SHARE) },
                    onNavigateToSplitTunnel = { navigateTo(Screen.SPLIT_TUNNEL) },
                    onNavigateToLogs = { navigateTo(Screen.LOGS) },
                    onNavigateToSettings = { navigateTo(Screen.SETTINGS) }
                )
                Screen.SERVERS -> ServersScreen(
                    onNavigateBack = { navigateBack() }
                )
                Screen.TWEAKS -> com.example.h25vpn.ui.screens.TweaksScreen(
                    onNavigateBack = { navigateBack() }
                )
                Screen.PROXY_SHARE -> ProxyShareScreen(
                    onNavigateBack = { navigateBack() }
                )
                Screen.SPLIT_TUNNEL -> SplitTunnelScreen(
                    onNavigateBack = { navigateBack() }
                )
                Screen.LOGS -> LogsScreen(
                    onNavigateBack = { navigateBack() }
                )
                Screen.SETTINGS -> SettingsScreen(
                    onNavigateBack = { navigateBack() }
                )
            }
        }
    }
}
