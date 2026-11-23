package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

object DeliveryStatus {
    const val SENDING = "SENDING"
    const val WAITING_FOR_NEARBY = "WAITING_FOR_NEARBY"
    const val FORWARDING = "FORWARDING"
    const val DELIVERED_TO_RECIPIENT = "DELIVERED_TO_RECIPIENT"
    const val RECEIVED_BY_GATEWAY = "RECEIVED_BY_GATEWAY"
    const val RECEIVED_FROM_NEARBY = "RECEIVED_FROM_NEARBY"
    const val SERVER_SYNCED = "SERVER_SYNCED"

    fun displayLabel(status: String): String = when (status) {
        SENDING -> "Sent"
        WAITING_FOR_NEARBY -> "Queued"
        FORWARDING -> "Forwarding"
        DELIVERED_TO_RECIPIENT -> "Delivered"
        RECEIVED_BY_GATEWAY -> "Received by rescue gateway"
        RECEIVED_FROM_NEARBY -> "Received"
        SERVER_SYNCED -> "Synced to authorities"
        else -> status
    }
}

object MessagePriority {
    const val CRITICAL_SOS = 100
    const val HIGH_SOS = 80
    const val MEDIUM_SOS = 60
    const val LOW_SOS = 40
    const val NORMAL = 20

    fun fromSosPriority(priority: String): Int = when (priority) {
        "Critical" -> CRITICAL_SOS
        "High" -> HIGH_SOS
        "Medium" -> MEDIUM_SOS
        else -> LOW_SOS
    }
}

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uniqueUserId: String,
    val name: String,
    val emailOrUsername: String,
    val phoneNumber: String,
    val password: String,
    val isDemo: Boolean = false
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerUserId: String,
    val name: String,
    val phoneNumber: String,
    val relation: String
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val contactDeviceId: String,
    val contactRole: String,
    val avatarInitials: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageUuid: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val recipientDeviceId: String = "RL-GW-MAIN",
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: String, // DeliveryStatus.*
    val priority: Int = MessagePriority.NORMAL,
    val retryCount: Int = 0,
    val hopPath: String = "Victim Device"
)

@Entity(tableName = "sos_alerts")
data class SosAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageUuid: String = UUID.randomUUID().toString(),
    val senderId: String = "RL-LOCAL",
    val senderName: String = "Me",
    val isFromMe: Boolean = true,
    val category: String, // Medical, Trapped, Food, Water, Other
    val priority: String, // Critical, High, Medium, Low
    val priorityRank: Int = MessagePriority.CRITICAL_SOS,
    val description: String,
    val locationLabel: String,
    val coordinates: String,
    val timestamp: Long,
    val deliveryStatus: String, // DeliveryStatus.*
    val retryCount: Int = 0,
    val hopPath: String
)

/**
 * Persisted deduplication registry to prevent duplicate forwarding or duplicate delivery
 * across multi-hop mesh relays and app restarts.
 */
@Entity(tableName = "seen_packets")
data class SeenPacketEntity(
    @PrimaryKey val packetUuid: String,
    val firstSeenAt: Long = System.currentTimeMillis()
)
