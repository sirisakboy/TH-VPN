package com.example.h25vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.h25vpn.data.ConnectionState
import com.example.h25vpn.ui.theme.CoralRed
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.EmeraldGreen
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary

@Composable
fun ConnectButton(
    connectionState: ConnectionState,
    sessionTimerText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = connectionState == ConnectionState.CONNECTING ||
            connectionState == ConnectionState.AUTHENTICATING
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isDisconnecting = connectionState == ConnectionState.DISCONNECTING

    val primaryColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldGreen
            isConnecting -> CyanAccent
            isDisconnecting -> CoralRed
            else -> CyanAccent
        },
        animationSpec = tween(400),
        label = "buttonColor"
    )

    // Pulse animation when connected or connecting
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isConnecting) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(190.dp)
        ) {
            // Outer glow ring
            if (isConnected || isConnecting) {
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f))
                )
            }

            // Medium ring border
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.1f))
                        ),
                        shape = CircleShape
                    )
            )

            // Inner main button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .shadow(
                        elevation = if (isConnected) 20.dp else 10.dp,
                        shape = CircleShape,
                        spotColor = primaryColor,
                        ambientColor = primaryColor
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = primaryColor,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(color = primaryColor),
                        onClick = onClick
                    )
                    .testTag("connect_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(54.dp),
                        color = CyanAccent,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "VPN Power Switch",
                        tint = primaryColor,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State Title
        val statusText = when (connectionState) {
            ConnectionState.DISCONNECTED -> "TAP TO CONNECT"
            ConnectionState.CONNECTING -> "CONNECTING..."
            ConnectionState.AUTHENTICATING -> "AUTHENTICATING..."
            ConnectionState.CONNECTED -> "CONNECTED & SECURE"
            ConnectionState.DISCONNECTING -> "DISCONNECTING..."
            ConnectionState.ERROR -> "CONNECTION FAILED"
        }

        Text(
            text = statusText,
            color = if (isConnected) EmeraldGreen else TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Duration or Subtext
        Text(
            text = if (isConnected) "Session: $sessionTimerText" else "Unlimited Free Bandwidth",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
