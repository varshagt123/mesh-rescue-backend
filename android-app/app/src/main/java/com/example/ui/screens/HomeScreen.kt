package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedSoft
import com.example.ui.theme.NavyMuted
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusOnlineBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealDark
import com.example.ui.theme.TealPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    userName: String,
    isOfflineMode: Boolean,
    bleRadioState: com.example.data.mesh.BleRadioState,
    discoveredPeers: List<com.example.data.mesh.MeshDevice>,
    pendingMessageCount: Int,
    recentConversations: List<ConversationEntity>,
    onRequestBluetoothAccess: () -> Unit,
    onOpenSos: () -> Unit,
    onOpenAllMessages: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    val firstName = userName.trim().split(" ").firstOrNull().orEmpty().ifBlank { "there" }
    val activePeerCount = discoveredPeers.count {
        it.connectionState != com.example.data.mesh.BleConnectionState.DISCONNECTED
    }
    val isConnected = !isOfflineMode &&
        bleRadioState == com.example.data.mesh.BleRadioState.READY &&
        activePeerCount > 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Simple Greeting + Honest Connected / Offline Status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hi, $firstName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DeepNavy
                    )
                    Text(
                        text = "Stay Connected. Stay Safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NavySecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isConnected) StatusOnlineBg else StatusWarningBg,
                    modifier = Modifier.testTag("home_connection_status")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusOnline else StatusWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Connected" else "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeepNavy
                        )
                    }
                }
            }
        }

        // Calm Bluetooth Permission / Enable Prompt (only shown when needed)
        if (!isOfflineMode && bleRadioState != com.example.data.mesh.BleRadioState.READY) {
            item {
                val message = when (bleRadioState) {
                    com.example.data.mesh.BleRadioState.PERMISSION_REQUIRED ->
                        "Allow Bluetooth access to message nearby devices without internet."
                    com.example.data.mesh.BleRadioState.BLUETOOTH_DISABLED ->
                        "Turn on Bluetooth to connect with nearby RescueLink devices."
                    com.example.data.mesh.BleRadioState.UNAVAILABLE ->
                        "Bluetooth Low Energy is unavailable on this device. Messages will stay queued locally."
                    else -> ""
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TealContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_ble_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepNavy,
                            modifier = Modifier.weight(1f)
                        )
                        if (bleRadioState != com.example.data.mesh.BleRadioState.UNAVAILABLE) {
                            TextButton(onClick = onRequestBluetoothAccess) {
                                Text(
                                    text = if (bleRadioState == com.example.data.mesh.BleRadioState.PERMISSION_REQUIRED) "Allow" else "Enable",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TealDark
                                )
                            }
                        }
                    }
                }
            }
        }

        // One Prominent Red SOS Button
        item {
            Surface(
                onClick = onOpenSos,
                shape = RoundedCornerShape(20.dp),
                color = EmergencyRed,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_sos_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PureWhite.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency SOS",
                            tint = PureWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Send SOS",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap for immediate emergency help",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }

        // Small Pending Message Notification (only when messages are waiting to send)
        if (pendingMessageCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusWarningBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_pending_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (pendingMessageCount == 1) {
                                "1 message waiting to send automatically"
                            } else {
                                "$pendingMessageCount messages waiting to send automatically"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepNavy
                        )
                    }
                }
            }
        }

        // Nearby Devices Section (Real BLE Discovery)
        item {
            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.titleLarge,
                color = DeepNavy
            )
        }

        if (discoveredPeers.isEmpty()) {
            item {
                Text(
                    text = "No nearby RescueLink devices found yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NavyMuted
                )
            }
        } else {
            items(discoveredPeers, key = { it.id }) { peer ->
                val (badgeBg, badgeFg) = when (peer.connectionState) {
                    com.example.data.mesh.BleConnectionState.GATT_CONNECTED -> StatusOnlineBg to StatusOnline
                    com.example.data.mesh.BleConnectionState.DISCOVERED -> TealContainer to TealDark
                    com.example.data.mesh.BleConnectionState.DISCONNECTED -> StatusWarningBg to StatusWarning
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("home_ble_peer_${peer.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peer.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = peer.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = NavySecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = peer.connectionState.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                HorizontalDivider(color = SubtleBorder.copy(alpha = 0.6f))
            }
        }

        // Recent Messages Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Messages",
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepNavy
                )
                TextButton(
                    onClick = onOpenAllMessages,
                    modifier = Modifier.testTag("home_see_all_messages")
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealPrimary
                    )
                }
            }
        }

        // Flat, Clean Recent Messages List
        items(recentConversations.take(4), key = { it.id }) { conversation ->
            ConversationListRow(
                conversation = conversation,
                onClick = { onOpenConversation(conversation.id) }
            )
            HorizontalDivider(color = SubtleBorder.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun ConversationListRow(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag("conversation_row_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (conversation.id == "conv_command") EmergencyRedSoft else TealContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.avatarInitials,
                style = MaterialTheme.typography.titleMedium,
                color = if (conversation.id == "conv_command") EmergencyRed else TealDark,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatShortTime(conversation.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NavyMuted
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NavySecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite
                        )
                    }
                }
            }
        }
    }
}

fun formatShortTime(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
