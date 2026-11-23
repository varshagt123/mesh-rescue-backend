package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RescueLinkDao {

    // User Accounts
    @Query("SELECT * FROM user_accounts WHERE uniqueUserId = :uniqueUserId LIMIT 1")
    fun observeUserByUniqueId(uniqueUserId: String): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE uniqueUserId = :uniqueUserId LIMIT 1")
    suspend fun getUserByUniqueId(uniqueUserId: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE LOWER(emailOrUsername) = LOWER(:identifier) AND password = :password LIMIT 1")
    suspend fun authenticateUser(identifier: String, password: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE LOWER(emailOrUsername) = LOWER(:identifier) LIMIT 1")
    suspend fun findUserByIdentifier(identifier: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity): Long

    @Update
    suspend fun updateUser(user: UserAccountEntity)

    // Emergency Contacts
    @Query("SELECT * FROM emergency_contacts WHERE ownerUserId = :ownerUserId ORDER BY id ASC")
    fun observeEmergencyContacts(ownerUserId: String): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :contactId")
    suspend fun deleteEmergencyContact(contactId: Int)

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE ownerUserId = :ownerUserId")
    suspend fun getEmergencyContactCount(ownerUserId: String): Int

    // Conversations
    @Query("SELECT * FROM conversations ORDER BY lastTimestamp DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getConversationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastTimestamp = :timestamp WHERE id = :conversationId")
    suspend fun updateConversationLastMessage(conversationId: String, lastMessage: String, timestamp: Long)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)

    // Messages
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status, retryCount = :retryCount, hopPath = :hopPath WHERE id = :messageId")
    suspend fun updateMessageDelivery(messageId: Int, status: String, retryCount: Int, hopPath: String)

    @Query(
        "SELECT * FROM messages WHERE isFromMe = 1 AND status IN ('SENDING', 'WAITING_FOR_NEARBY', 'FORWARDING') " +
            "ORDER BY priority DESC, timestamp ASC"
    )
    suspend fun getPendingOutboundMessages(): List<MessageEntity>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE isFromMe = 1 AND status IN ('SENDING', 'WAITING_FOR_NEARBY', 'FORWARDING')"
    )
    fun observePendingOutboundCount(): Flow<Int>

    // SOS Alerts
    @Query("SELECT * FROM sos_alerts ORDER BY timestamp DESC")
    fun observeSosAlerts(): Flow<List<SosAlertEntity>>

    @Query("SELECT COUNT(*) FROM sos_alerts")
    suspend fun getSosAlertCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSosAlert(alert: SosAlertEntity): Long

    @Query("UPDATE sos_alerts SET deliveryStatus = :status, retryCount = :retryCount, hopPath = :hopPath WHERE id = :alertId")
    suspend fun updateSosAlertDelivery(alertId: Int, status: String, retryCount: Int, hopPath: String)

    @Query(
        "SELECT * FROM sos_alerts WHERE isFromMe = 1 AND deliveryStatus IN ('SENDING', 'WAITING_FOR_NEARBY', 'FORWARDING') " +
            "ORDER BY priorityRank DESC, timestamp ASC"
    )
    suspend fun getPendingSosAlerts(): List<SosAlertEntity>

    // Packet Deduplication
    @Query("SELECT EXISTS(SELECT 1 FROM seen_packets WHERE packetUuid = :packetUuid)")
    suspend fun hasSeenPacket(packetUuid: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordSeenPacket(packet: SeenPacketEntity)
}
