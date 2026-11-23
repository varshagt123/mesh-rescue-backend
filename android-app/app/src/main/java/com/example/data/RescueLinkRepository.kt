package com.example.data

import com.example.data.local.ConversationEntity
import com.example.data.local.DeliveryStatus
import com.example.data.local.EmergencyContactEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessagePriority
import com.example.data.local.RescueLinkDao
import com.example.data.local.SeenPacketEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SosAlertEntity
import com.example.data.local.UserAccountEntity
import kotlinx.coroutines.flow.Flow

class RescueLinkRepository(
    private val dao: RescueLinkDao,
    private val sessionPreferences: SessionPreferences
) {
    val loggedInUserId: Flow<String?> = sessionPreferences.loggedInUserId
    val isOfflineMode: Flow<Boolean> = sessionPreferences.isOfflineMode
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val sosAlerts: Flow<List<SosAlertEntity>> = dao.observeSosAlerts()
    val pendingOutboundCount: Flow<Int> = dao.observePendingOutboundCount()

    fun observeUser(uniqueUserId: String): Flow<UserAccountEntity?> =
        dao.observeUserByUniqueId(uniqueUserId)

    fun observeEmergencyContacts(ownerUserId: String): Flow<List<EmergencyContactEntity>> =
        dao.observeEmergencyContacts(ownerUserId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun getConversationById(conversationId: String): ConversationEntity? =
        dao.getConversationById(conversationId)

    suspend fun setLoggedInUserId(uniqueUserId: String?) =
        sessionPreferences.setLoggedInUserId(uniqueUserId)

    suspend fun setOfflineMode(offline: Boolean) =
        sessionPreferences.setOfflineMode(offline)

    suspend fun login(identifier: String, password: String): UserAccountEntity? {
        return dao.authenticateUser(identifier.trim(), password)
    }

    suspend fun register(name: String, phone: String, emailOrUsername: String, password: String): Result<UserAccountEntity> {
        val trimmedId = emailOrUsername.trim()
        if (dao.findUserByIdentifier(trimmedId) != null) {
            return Result.failure(IllegalArgumentException("An account with this email or username already exists."))
        }
        val uniqueId = "RL-${(1000..9999).random()}-${('A'..'Z').random()}"
        val user = UserAccountEntity(
            uniqueUserId = uniqueId,
            name = name.trim(),
            emailOrUsername = trimmedId,
            phoneNumber = phone.trim(),
            password = password,
            isDemo = false
        )
        dao.insertUser(user)
        seedDefaultEmergencyContactsIfNeeded(uniqueId)
        return Result.success(user)
    }

    suspend fun getOrCreateDemoUser(): UserAccountEntity {
        val demoId = "RL-DEMO-01"
        val existing = dao.getUserByUniqueId(demoId)
        if (existing != null) {
            seedDefaultEmergencyContactsIfNeeded(demoId)
            return existing
        }
        val demoUser = UserAccountEntity(
            uniqueUserId = demoId,
            name = "Alex Rivera",
            emailOrUsername = "demo@rescuelink.org",
            phoneNumber = "+1 (555) 234-8910",
            password = "demo",
            isDemo = true
        )
        dao.insertUser(demoUser)
        seedDefaultEmergencyContactsIfNeeded(demoId)
        return demoUser
    }

    suspend fun updateUserProfile(user: UserAccountEntity) {
        dao.updateUser(user)
    }

    suspend fun addEmergencyContact(ownerUserId: String, name: String, phone: String, relation: String) {
        dao.insertEmergencyContact(
            EmergencyContactEntity(
                ownerUserId = ownerUserId,
                name = name.trim(),
                phoneNumber = phone.trim(),
                relation = relation.trim()
            )
        )
    }

    suspend fun removeEmergencyContact(contactId: Int) {
        dao.deleteEmergencyContact(contactId)
    }

    suspend fun insertMessage(message: MessageEntity): Long {
        val id = dao.insertMessage(message)
        dao.updateConversationLastMessage(message.conversationId, message.content, message.timestamp)
        return id
    }

    suspend fun updateMessageDelivery(messageId: Int, status: String, retryCount: Int, hopPath: String) {
        dao.updateMessageDelivery(messageId, status, retryCount, hopPath)
    }

    suspend fun getPendingOutboundMessages(): List<MessageEntity> =
        dao.getPendingOutboundMessages()

    suspend fun clearUnread(conversationId: String) = dao.clearUnreadCount(conversationId)

    suspend fun insertSosAlert(alert: SosAlertEntity): Long = dao.insertSosAlert(alert)

    suspend fun updateSosAlertDelivery(alertId: Int, status: String, retryCount: Int, hopPath: String) =
        dao.updateSosAlertDelivery(alertId, status, retryCount, hopPath)

    suspend fun getPendingSosAlerts(): List<SosAlertEntity> =
        dao.getPendingSosAlerts()

    suspend fun hasSeenPacket(packetUuid: String): Boolean =
        dao.hasSeenPacket(packetUuid)

    suspend fun recordSeenPacket(packetUuid: String) =
        dao.recordSeenPacket(SeenPacketEntity(packetUuid = packetUuid))

    suspend fun seedInitialDemoDataIfNeeded() {
        val now = System.currentTimeMillis()
        if (dao.getConversationCount() == 0) {
            val convs = listOf(
                ConversationEntity(
                    id = "conv_command",
                    contactName = "Rescue Command",
                    contactDeviceId = "RL-GW-MAIN",
                    contactRole = "Rescue Gateway",
                    avatarInitials = "RC",
                    lastMessage = "Sector 4 shelter has potable water and charging stations.",
                    lastTimestamp = now - 12 * 60_000L,
                    unreadCount = 1,
                    isOnline = true
                ),
                ConversationEntity(
                    id = "conv_medic",
                    contactName = "Dr. Priya Patel",
                    contactDeviceId = "RL-PEER-14",
                    contactRole = "Field Medic",
                    avatarInitials = "PP",
                    lastMessage = "On my way to the community center with first-aid kits.",
                    lastTimestamp = now - 38 * 60_000L,
                    unreadCount = 0,
                    isOnline = true
                ),
                ConversationEntity(
                    id = "conv_maya",
                    contactName = "Maya Lin",
                    contactDeviceId = "RL-PEER-29",
                    contactRole = "Shelter Volunteer",
                    avatarInitials = "ML",
                    lastMessage = "Can you confirm headcount at Oak St?",
                    lastTimestamp = now - 95 * 60_000L,
                    unreadCount = 2,
                    isOnline = false
                ),
                ConversationEntity(
                    id = "conv_relay",
                    contactName = "Responder Unit Alpha",
                    contactDeviceId = "RL-RELAY-01",
                    contactRole = "Mesh Relay Node",
                    avatarInitials = "RA",
                    lastMessage = "Relay signal strong. Forwarding queued packets.",
                    lastTimestamp = now - 180 * 60_000L,
                    unreadCount = 0,
                    isOnline = true
                )
            )
            convs.forEach { dao.insertConversation(it) }

            val initialMessages = listOf(
                MessageEntity(
                    conversationId = "conv_command",
                    recipientDeviceId = "RL-GW-MAIN",
                    senderName = "Rescue Command",
                    content = "Mesh broadcast active. Cellular towers in Sector 4 are down.",
                    timestamp = now - 25 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                ),
                MessageEntity(
                    conversationId = "conv_command",
                    recipientDeviceId = "RL-GW-MAIN",
                    senderName = "Me",
                    content = "Copy that. We have 6 people sheltering safely at Elm Street.",
                    timestamp = now - 18 * 60_000L,
                    isFromMe = true,
                    status = DeliveryStatus.RECEIVED_BY_GATEWAY,
                    hopPath = "Victim → Relay 1 → Relay 2 → Gateway"
                ),
                MessageEntity(
                    conversationId = "conv_command",
                    recipientDeviceId = "RL-GW-MAIN",
                    senderName = "Rescue Command",
                    content = "Sector 4 shelter has potable water and charging stations.",
                    timestamp = now - 12 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                ),
                MessageEntity(
                    conversationId = "conv_medic",
                    recipientDeviceId = "RL-PEER-14",
                    senderName = "Dr. Priya Patel",
                    content = "Checking in via mesh. Any urgent medical needs nearby?",
                    timestamp = now - 45 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                ),
                MessageEntity(
                    conversationId = "conv_medic",
                    recipientDeviceId = "RL-PEER-14",
                    senderName = "Me",
                    content = "Minor sprain here, no critical injuries. Thank you!",
                    timestamp = now - 42 * 60_000L,
                    isFromMe = true,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT,
                    hopPath = "Victim → Dr. Priya Patel"
                ),
                MessageEntity(
                    conversationId = "conv_medic",
                    recipientDeviceId = "RL-PEER-14",
                    senderName = "Dr. Priya Patel",
                    content = "On my way to the community center with first-aid kits.",
                    timestamp = now - 38 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                ),
                MessageEntity(
                    conversationId = "conv_maya",
                    recipientDeviceId = "RL-PEER-29",
                    senderName = "Maya Lin",
                    content = "Can you confirm headcount at Oak St?",
                    timestamp = now - 95 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                ),
                MessageEntity(
                    conversationId = "conv_relay",
                    recipientDeviceId = "RL-RELAY-01",
                    senderName = "Responder Unit Alpha",
                    content = "Relay signal strong. Forwarding queued packets.",
                    timestamp = now - 180 * 60_000L,
                    isFromMe = false,
                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT
                )
            )
            initialMessages.forEach { dao.insertMessage(it) }
        }

        if (dao.getSosAlertCount() == 0) {
            dao.insertSosAlert(
                SosAlertEntity(
                    category = "Water",
                    priority = "Medium",
                    priorityRank = MessagePriority.MEDIUM_SOS,
                    description = "Drinking water supply running low for 6 residents at Elm St.",
                    locationLabel = "Sector 4 — Elm St Community Hall",
                    coordinates = "37.7749° N, 122.4194° W",
                    timestamp = now - 70 * 60_000L,
                    deliveryStatus = DeliveryStatus.SERVER_SYNCED,
                    hopPath = "Victim → Relay 1 → Relay 2 → Gateway → Authorities"
                )
            )
        }
    }

    private suspend fun seedDefaultEmergencyContactsIfNeeded(ownerUserId: String) {
        if (dao.getEmergencyContactCount(ownerUserId) == 0) {
            dao.insertEmergencyContact(
                EmergencyContactEntity(
                    ownerUserId = ownerUserId,
                    name = "Elena Rivera",
                    phoneNumber = "+1 (555) 890-4412",
                    relation = "Sister"
                )
            )
            dao.insertEmergencyContact(
                EmergencyContactEntity(
                    ownerUserId = ownerUserId,
                    name = "District 4 Emergency Desk",
                    phoneNumber = "+1 (555) 911-0040",
                    relation = "Local Dispatch"
                )
            )
        }
    }
}
