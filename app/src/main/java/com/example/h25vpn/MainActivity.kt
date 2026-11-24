package com.example.h25vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.ui.screens.HomeScreen
import com.example.h25vpn.ui.screens.LogsScreen
import com.example.h25vpn.ui.screens.PromoHubScreen
import com.example.h25vpn.ui.screens.ProxyShareScreen
import com.example.h25vpn.ui.screens.ServersScreen
import com.example.h25vpn.ui.screens.SettingsScreen
import com.example.h25vpn.ui.screens.SplitTunnelScreen
import com.example.h25vpn.ui.screens.TweaksScreen
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.H25VPNTheme
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary

enum class Screen {
    HOME,
    SERVERS,
    PROMO_HUB,
    TWEAKS,
    PROXY_SHARE,
    SPLIT_TUNNEL,
    LOGS,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VpnRepository.initialize(applicationContext)

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
        if (currentScreen != screen) {
            navStack.add(screen)
        }
    }

    fun switchRootTab(screen: Screen) {
        navStack.clear()
        navStack.add(Screen.HOME)
        if (screen != Screen.HOME) {
            navStack.add(screen)
        }
    }

    fun navigateBack() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.lastIndex)
        }
    }

    BackHandler(enabled = navStack.size > 1) {
        navigateBack()
    }

    val topLevelTabs = listOf(
        Triple(Screen.HOME, "หน้าแรก", Icons.Default.Home),
        Triple(Screen.SERVERS, "เซิร์ฟเวอร์", Icons.Default.Public),
        Triple(Screen.PROMO_HUB, "โปรซิมไทย", Icons.Default.SimCard),
        Triple(Screen.TWEAKS, "ค่าย/SNI", Icons.Default.CellTower),
        Triple(Screen.SETTINGS, "ตั้งค่า", Icons.Default.Settings)
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = DarkBg,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 4.dp
            ) {
                topLevelTabs.forEach { (tabScreen, label, icon) ->
                    val isSelected = currentScreen == tabScreen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { switchRootTab(tabScreen) },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = TextPrimary,
                            indicatorColor = DarkSurfaceVariant,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
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
                    onNavigateToPromoHub = { navigateTo(Screen.PROMO_HUB) },
                    onNavigateToProxyShare = { navigateTo(Screen.PROXY_SHARE) },
                    onNavigateToSplitTunnel = { navigateTo(Screen.SPLIT_TUNNEL) },
                    onNavigateToLogs = { navigateTo(Screen.LOGS) },
                    onNavigateToSettings = { navigateTo(Screen.SETTINGS) }
                )

                Screen.SERVERS -> ServersScreen(
                    onServerSelected = { switchRootTab(Screen.HOME) }
                )

                Screen.PROMO_HUB -> PromoHubScreen(
                    onPromoApplied = { switchRootTab(Screen.HOME) }
                )

                Screen.TWEAKS -> TweaksScreen(
                    onNavigateBack = { navigateBack() }
                )

                Screen.PROXY_SHARE -> ProxyShareScreen(
                    onNavigateBack = { navigateBack() }
                )

                Screen.SPLIT_TUNNEL -> SplitTunnelScreen(
                    onBack = { navigateBack() }
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
