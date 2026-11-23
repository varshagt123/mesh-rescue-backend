package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DeliveryStatus
import com.example.data.local.SosAlertEntity
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.EmergencyRedSoft
import com.example.ui.theme.NavyMuted
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftSurface
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusOnlineBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealDark
import com.example.ui.theme.TealPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SosScreen(
    activeProgress: SosAlertEntity?,
    sosAlerts: List<SosAlertEntity>,
    onSendSos: (category: String, priority: String, description: String, locationLabel: String, coordinates: String) -> Unit
) {
    val categories = listOf("Medical", "Trapped", "Food", "Water", "Other")
    var selectedCategory by rememberSaveable { mutableStateOf("Medical") }
    var description by rememberSaveable { mutableStateOf("") }
    var shareLocation by rememberSaveable { mutableStateOf(true) }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }

    val latestSentSos = activeProgress?.takeIf { it.isFromMe } ?: sosAlerts.firstOrNull { it.isFromMe }
    val receivedSosList = sosAlerts.filter { !it.isFromMe }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Emergency SOS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DeepNavy
                )
                Text(
                    text = "Tap the button below to request urgent help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NavySecondary
                )
            }
        }

        // One Large Red SOS Button
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(EmergencyRedContainer.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            onSendSos(
                                selectedCategory,
                                "Critical",
                                description,
                                if (shareLocation) "Current Location Shared" else "Location Not Shared",
                                if (shareLocation) "37.7749° N, 122.4194° W" else "Hidden"
                            )
                            description = ""
                            showConfirmation = true
                        },
                        shape = CircleShape,
                        color = EmergencyRed,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(148.dp)
                            .testTag("sos_big_button")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Send SOS",
                                tint = PureWhite,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "SOS",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Clear Confirmation Banner after Sending
        if (showConfirmation) {
            item {
                Surface(
                    color = StatusOnlineBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sos_confirmation_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusOnline,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "SOS saved. We will keep trying to deliver it automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepNavy
                        )
                    }
                }
            }
        }

        // Simple Emergency Options: Medical, Trapped, Food, Water, Other
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "What do you need?",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmergencyRedSoft,
                                selectedLabelColor = EmergencyRed,
                                containerColor = SoftSurface,
                                labelColor = DeepNavy
                            ),
                            modifier = Modifier.testTag("sos_cat_${category.lowercase()}")
                        )
                    }
                }
            }
        }

        // Short Description Field
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short description (optional)") },
                placeholder = { Text("Number of people, injuries, or landmarks...", color = NavyMuted) },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    unfocusedBorderColor = SubtleBorder,
                    focusedLabelColor = TealPrimary,
                    focusedTextColor = DeepNavy,
                    unfocusedTextColor = DeepNavy
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_description_input")
            )
        }

        // Share Location Option
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Share my location",
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepNavy
                        )
                        Text(
                            text = if (shareLocation) "Location will be included with SOS" else "Location hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = NavySecondary
                        )
                    }
                }
                Switch(
                    checked = shareLocation,
                    onCheckedChange = { shareLocation = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = TealPrimary
                    ),
                    modifier = Modifier.testTag("sos_share_location_switch")
                )
            }
        }

        // Section 1: Sent SOS (Your SOS)
        if (latestSentSos != null) {
            item {
                HorizontalDivider(color = SubtleBorder)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Your Sent SOS",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latestSentSos.category,
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepNavy
                        )
                        if (latestSentSos.description.isNotBlank()) {
                            Text(
                                text = latestSentSos.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = NavySecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Last updated ${formatShortTime(latestSentSos.timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    SentSosStatusPill(status = latestSentSos.deliveryStatus)
                }
            }
        }

        // Section 2: Received SOS from Nearby Phones
        if (receivedSosList.isNotEmpty()) {
            item {
                HorizontalDivider(color = SubtleBorder)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Received SOS Nearby (${receivedSosList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy
                )
            }

            items(receivedSosList, key = { it.id }) { incoming ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "From ${incoming.senderName} • ${incoming.category}",
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepNavy
                        )
                        if (incoming.description.isNotBlank()) {
                            Text(
                                text = incoming.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = NavySecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Received ${formatShortTime(incoming.timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyMuted
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = TealContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Received",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SentSosStatusPill(status: String) {
    val (bg, fg, label) = when (status) {
        DeliveryStatus.WAITING_FOR_NEARBY -> Triple(StatusWarningBg, StatusWarning, "Queued")
        DeliveryStatus.SENDING, DeliveryStatus.FORWARDING -> Triple(TealContainer, TealDark, "Sent")
        DeliveryStatus.DELIVERED_TO_RECIPIENT -> Triple(StatusOnlineBg, StatusOnline, "Delivered")
        else -> Triple(SoftSurface, NavySecondary, DeliveryStatus.displayLabel(status))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
