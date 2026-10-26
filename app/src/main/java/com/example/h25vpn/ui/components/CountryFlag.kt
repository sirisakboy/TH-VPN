package com.example.h25vpn.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CountryFlag(
    countryCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val context = LocalContext.current
    val normalizedCode = remember(countryCode) { countryCode.uppercase().trim() }

    val flagBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, normalizedCode) {
        value = try {
            val assetPath = "flag/$normalizedCode.png"
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E293B)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = flagBitmap.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Flag for $countryCode",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = countryCode,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(size * 0.75f)
            )
        }
    }
}
