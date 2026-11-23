package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.local.ConversationEntity
import com.example.data.local.DeliveryStatus
import com.example.data.local.MessageEntity
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.MutedSurface
import com.example.ui.theme.NavyMuted
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftSurface
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealDark
import com.example.ui.theme.TealPrimary

@Composable
fun ChatsListScreen(
    conversations: List<ConversationEntity>,
    onOpenConversation: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filtered = conversations.filter {
        searchQuery.isBlank() ||
            it.contactName.contains(searchQuery, ignoreCase = true) ||
            it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineMedium,
            color = DeepNavy
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search messages", color = NavyMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NavyMuted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = SubtleBorder,
                focusedContainerColor = SoftSurface,
                unfocusedContainerColor = SoftSurface,
                focusedTextColor = DeepNavy,
                unfocusedTextColor = DeepNavy
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chats_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered, key = { it.id }) { conversation ->
                ConversationListRow(
                    conversation = conversation,
                    onClick = { onOpenConversation(conversation.id) }
                )
                HorizontalDivider(color = SubtleBorder.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun ChatDetailScreen(
    conversation: ConversationEntity,
    messages: List<MessageEntity>,
    isOfflineMode: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Simple WhatsApp-like Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("chat_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DeepNavy
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TealContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.avatarInitials,
                    style = MaterialTheme.typography.titleSmall,
                    color = TealDark,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy
                )
                Text(
                    text = conversation.contactRole,
                    style = MaterialTheme.typography.bodySmall,
                    color = NavySecondary
                )
            }
        }

        HorizontalDivider(color = SubtleBorder)

        if (isOfflineMode) {
            Surface(
                color = StatusWarningBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Offline — messages will send automatically when a connection is found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepNavy,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(SoftSurface),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        HorizontalDivider(color = SubtleBorder)

        // Message Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Type a message...", color = NavyMuted) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    unfocusedBorderColor = SubtleBorder,
                    focusedContainerColor = SoftSurface,
                    unfocusedContainerColor = SoftSurface,
                    focusedTextColor = DeepNavy,
                    unfocusedTextColor = DeepNavy
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSendMessage(draft)
                        draft = ""
                    }
                },
                shape = CircleShape,
                color = if (draft.isNotBlank()) TealPrimary else MutedSurface,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (draft.isNotBlank()) PureWhite else NavyMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromMe) TealPrimary else PureWhite
    val textColor = if (message.isFromMe) PureWhite else DeepNavy
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isFromMe) 18.dp else 4.dp,
        bottomEnd = if (message.isFromMe) 4.dp else 18.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            shadowElevation = if (message.isFromMe) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formatShortTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isFromMe) PureWhite.copy(alpha = 0.8f) else NavyMuted
                    )
                    if (message.isFromMe) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val (icon, label) = when (message.status) {
                            DeliveryStatus.WAITING_FOR_NEARBY -> Icons.Default.Schedule to "Pending"
                            DeliveryStatus.SENDING, DeliveryStatus.FORWARDING -> Icons.Default.Check to "Sent"
                            DeliveryStatus.DELIVERED_TO_RECIPIENT,
                            DeliveryStatus.RECEIVED_BY_GATEWAY,
                            DeliveryStatus.SERVER_SYNCED -> Icons.Default.DoneAll to "Delivered"
                            else -> Icons.Default.Schedule to "Pending"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = PureWhite.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
