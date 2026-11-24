package com.example.h25vpn.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.h25vpn.R
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurfaceVariant

@Composable
fun CarrierBadgeIcon(
    carrier: String,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    val resId = when {
        carrier.contains("AIS", ignoreCase = true) -> R.drawable.ais
        carrier.contains("DTAC", ignoreCase = true) -> R.drawable.dtac
        carrier.contains("True", ignoreCase = true) -> R.drawable.ic_true
        carrier.contains("My", ignoreCase = true) -> R.drawable.my
        carrier.contains("Direct", ignoreCase = true) -> R.drawable.direct
        else -> R.drawable.free
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = carrier,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}
