package com.example.h25vpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.h25vpn.data.CarrierTweak
import com.example.h25vpn.data.TunnelProtocol
import com.example.h25vpn.data.VpnRepository
import com.example.h25vpn.ui.theme.CyanAccent
import com.example.h25vpn.ui.theme.DarkBg
import com.example.h25vpn.ui.theme.DarkBorder
import com.example.h25vpn.ui.theme.DarkSurface
import com.example.h25vpn.ui.theme.DarkSurfaceVariant
import com.example.h25vpn.ui.theme.PurpleAccent
import com.example.h25vpn.ui.theme.TextMuted
import com.example.h25vpn.ui.theme.TextPrimary
import com.example.h25vpn.ui.theme.TextSecondary
import java.util.UUID

@Composable
fun TweaksScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val tweaks by VpnRepository.tweaks.collectAsState()
    val selectedTweak by VpnRepository.selectedTweak.collectAsState()

    var selectedCarrierFilter by remember { mutableStateOf("All") }
    var showCustomDialog by remember { mutableStateOf(false) }

    val carrierFilters = listOf("All", "AIS", "DTAC", "TrueMove", "Smart", "Globe", "TNT", "Direct", "Gaming", "Custom")

    val filteredTweaks = remember(tweaks, selectedCarrierFilter) {
        if (selectedCarrierFilter == "All") {
            tweaks
        } else if (selectedCarrierFilter == "Custom") {
            tweaks.filter { it.isCustom }
        } else {
            tweaks.filter { it.carrier.equals(selectedCarrierFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp).testTag("tweaks_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Carrier Tweaks & SNI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select network payload configuration",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Custom Payload button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PurpleAccent.copy(alpha = 0.2f))
                    .border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { showCustomDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("add_custom_tweak_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Custom",
                        tint = PurpleAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Custom SNI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Carrier Filters
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(carrierFilters) { filter ->
                val selected = selectedCarrierFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { selectedCarrierFilter = filter },
                    label = { Text(filter, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurpleAccent,
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = if (selected) PurpleAccent else DarkBorder
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("carrier_filter_$filter")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tweaks List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredTweaks, key = { it.id }) { tweak ->
                val isSelected = tweak.id == selectedTweak.id
                TweakListItem(
                    tweak = tweak,
                    isSelected = isSelected,
                    onSelect = {
                        VpnRepository.selectTweak(tweak)
                        onNavigateBack()
                    }
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomPayloadDialog(
            onDismiss = { showCustomDialog = false },
            onSave = { newTweak ->
                VpnRepository.addCustomTweak(newTweak)
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun TweakListItem(
    tweak: CarrierTweak,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) PurpleAccent else DarkBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag("tweak_item_${tweak.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PurpleAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = tweak.carrier,
                    tint = PurpleAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tweak.carrier,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${tweak.protocol.displayName}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = tweak.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = tweak.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (tweak.bugHostSni.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SNI: ${tweak.bugHostSni}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = PurpleAccent,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, DarkBorder, CircleShape)
            )
        }
    }
}

@Composable
fun CustomPayloadDialog(
    onDismiss: () -> Unit,
    onSave: (CarrierTweak) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("Custom") }
    var sniHost by remember { mutableStateOf("") }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPortStr by remember { mutableStateOf("8080") }
    var payloadText by remember { mutableStateOf("CONNECT [host_port] [protocol][crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]") }
    var selectedProtocol by remember { mutableStateOf(TunnelProtocol.SSH_SSL) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkBg)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom SNI & Payload",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Config Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Config Name", color = TextMuted, fontSize = 12.sp) },
                placeholder = { Text("e.g. My Line SNI Free", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("custom_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bug Host / SNI
            OutlinedTextField(
                value = sniHost,
                onValueChange = { sniHost = it },
                label = { Text("SNI / Bug Host (SSL)", color = TextMuted, fontSize = 12.sp) },
                placeholder = { Text("e.g. m.tiktok.com or line.me", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("custom_sni_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // HTTP Payload template
            OutlinedTextField(
                value = payloadText,
                onValueChange = { payloadText = it },
                label = { Text("HTTP Payload String", color = TextMuted, fontSize = 12.sp) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("custom_payload_input")
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val finalName = if (name.isBlank()) "Custom Tweak" else name
                    val finalSni = if (sniHost.isBlank()) "cloudflare.com" else sniHost
                    val port = proxyPortStr.toIntOrNull() ?: 8080
                    val newTweak = CarrierTweak(
                        id = UUID.randomUUID().toString(),
                        name = finalName,
                        carrier = carrier,
                        description = "Custom user configuration",
                        protocol = selectedProtocol,
                        bugHostSni = finalSni,
                        proxyHost = proxyHost,
                        proxyPort = port,
                        customPayload = payloadText,
                        isCustom = true
                    )
                    onSave(newTweak)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_custom_tweak_button")
            ) {
                Text("Save & Apply Payload", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
