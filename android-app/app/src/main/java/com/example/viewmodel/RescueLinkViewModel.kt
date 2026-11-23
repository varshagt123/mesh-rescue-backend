package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RescueLinkRepository
import com.example.data.local.ConversationEntity
import com.example.data.local.DeliveryStatus
import com.example.data.local.EmergencyContactEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessagePriority
import com.example.data.local.RescueLinkDatabase
import com.example.data.local.SessionPreferences
import com.example.data.local.SosAlertEntity
import com.example.data.local.UserAccountEntity
import com.example.data.mesh.BlePacketPayload
import com.example.data.mesh.BleRadioState
import com.example.data.mesh.MeshDevice
import com.example.data.mesh.MeshNetworkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RescueLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RescueLinkDatabase.getInstance(application)
    private val sessionPreferences = SessionPreferences(application)
    private val repository = RescueLinkRepository(database.dao(), sessionPreferences)
    private val meshManager = MeshNetworkManager(application)
    private val storeForwardMutex = Mutex()

    private val _sessionLoaded = MutableStateFlow(false)
    val sessionLoaded: StateFlow<Boolean> = _sessionLoaded.asStateFlow()

    val loggedInUserId: StateFlow<String?> = repository.loggedInUserId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentUser: StateFlow<UserAccountEntity?> = loggedInUserId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null) else repository.observeUser(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emergencyContacts: StateFlow<List<EmergencyContactEntity>> = loggedInUserId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList()) else repository.observeEmergencyContacts(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOfflineMode: StateFlow<Boolean> = repository.isOfflineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sosAlerts: StateFlow<List<SosAlertEntity>> = repository.sosAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingMessageCount: StateFlow<Int> = repository.pendingOutboundCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bleRadioState: StateFlow<BleRadioState> = meshManager.radioState
    val discoveredPeers: StateFlow<List<MeshDevice>> = meshManager.discoveredDevices

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _activeSosProgress = MutableStateFlow<SosAlertEntity?>(null)
    val activeSosProgress: StateFlow<SosAlertEntity?> = _activeSosProgress.asStateFlow()

    private var localPersistentDeviceId: String = "RL-0000"

    init {
        viewModelScope.launch {
            localPersistentDeviceId = sessionPreferences.getOrCreateDeviceId()
            meshManager.updateLocalIdentity(localPersistentDeviceId, currentUser.value?.name ?: "RescueLink")
            repository.seedInitialDemoDataIfNeeded()
            repository.loggedInUserId.collect {
                _sessionLoaded.value = true
            }
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val devId = sessionPreferences.getOrCreateDeviceId()
                    localPersistentDeviceId = devId
                    meshManager.updateLocalIdentity(devId, user.name)
                }
            }
        }
        meshManager.startMesh()
        observeIncomingBlePackets()
        observeDiscoveredPeersForAutoRetry()
        startAutomaticStoreAndForwardLoop()
    }

    override fun onCleared() {
        super.onCleared()
        meshManager.stopMesh()
    }

    fun requiredBluetoothPermissions(): Array<String> = meshManager.requiredPermissions()

    fun onBluetoothPermissionsOrStateChanged() {
        meshManager.refreshRadioState()
        if (meshManager.hasRequiredPermissions()) {
            meshManager.startMesh()
            viewModelScope.launch {
                processStoreAndForwardQueue()
            }
        }
    }

    /**
     * Listens for real incoming BLE packets from nearby RescueLink devices:
     * 1. Deduplicates using Room's `seen_packets` table.
     * 2. Saves incoming messages or SOS alerts locally.
     * 3. Relays eligible packets (ttl > 1) to other discovered BLE peers.
     */
    private fun observeIncomingBlePackets() {
        viewModelScope.launch {
            meshManager.incomingPackets.collect { incoming ->
                val packet = incoming.payload
                if (packet.type == "ACK") return@collect

                // A packet is processed only once on each device. This prevents mesh loops.
                if (repository.hasSeenPacket(packet.id)) return@collect
                repository.recordSeenPacket(packet.id)

                val isBroadcast = packet.recipientId == "BROADCAST"
                val isForThisDevice = packet.recipientId == localPersistentDeviceId
                val shouldDisplayHere = isBroadcast || isForThisDevice

                // Intermediate phones must relay unicast packets, but must NOT show them
                // as messages addressed to themselves.
                if (shouldDisplayHere) {
                    when (packet.type) {
                        "MSG" -> {
                            val convId = "ble_${packet.senderId}"
                            val existingConv = repository.getConversationById(convId)
                            if (existingConv == null) {
                                val initials = packet.senderName
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().uppercase() }
                                    .ifBlank { "RL" }

                                database.dao().insertConversation(
                                    ConversationEntity(
                                        id = convId,
                                        contactName = packet.senderName,
                                        // Keep the sender's stable RescueLink ID, not the
                                        // temporary Bluetooth MAC address of the previous hop.
                                        contactDeviceId = packet.senderId,
                                        contactRole = "RescueLink Device",
                                        avatarInitials = initials,
                                        lastMessage = packet.body,
                                        lastTimestamp = packet.timestamp,
                                        unreadCount = 1,
                                        isOnline = true
                                    )
                                )
                            }

                            repository.insertMessage(
                                MessageEntity(
                                    messageUuid = packet.id,
                                    conversationId = convId,
                                    recipientDeviceId = localPersistentDeviceId,
                                    senderName = packet.senderName,
                                    content = packet.body,
                                    timestamp = packet.timestamp,
                                    isFromMe = false,
                                    status = DeliveryStatus.DELIVERED_TO_RECIPIENT,
                                    priority = packet.priority,
                                    hopPath = "Received via mesh from ${packet.senderId}"
                                )
                            )
                        }

                        "SOS" -> {
                            repository.insertSosAlert(
                                SosAlertEntity(
                                    messageUuid = packet.id,
                                    senderId = packet.senderId,
                                    senderName = packet.senderName,
                                    isFromMe = false,
                                    category = packet.category.ifBlank { "Emergency" },
                                    priority = "Critical",
                                    priorityRank = packet.priority,
                                    description = packet.body,
                                    locationLabel = packet.location.ifBlank { "Location not shared" },
                                    coordinates = "",
                                    timestamp = packet.timestamp,
                                    deliveryStatus = DeliveryStatus.RECEIVED_FROM_NEARBY,
                                    hopPath = "Received via mesh"
                                )
                            )
                        }
                    }
                }

                // Flood to every currently reachable neighbour except the previous hop.
                // Each neighbour's seen_packets table suppresses duplicates, so multiple
                // possible routes can coexist without creating endless forwarding loops.
                if (packet.ttl > 1) {
                    val relayPayload = packet.copy(ttl = packet.ttl - 1)
                    val nextHops = discoveredPeers.value.filter {
                        it.bleAddress != incoming.sourceAddress &&
                            it.connectionState != com.example.data.mesh.BleConnectionState.DISCONNECTED
                    }

                    for (peer in nextHops) {
                        meshManager.sendPacketToPeer(peer.bleAddress, relayPayload)
                    }
                }
            }
        }
    }

    private fun observeDiscoveredPeersForAutoRetry() {
        viewModelScope.launch {
            discoveredPeers.collect { peers ->
                android.util.Log.i("RescueLinkBLE", "[ViewModel] discoveredPeers updated: count=${peers.size} (${peers.map { it.id }})")
                if (peers.isNotEmpty() && !isOfflineMode.value) {
                    processStoreAndForwardQueue()
                }
            }
        }
    }

    private fun startAutomaticStoreAndForwardLoop() {
        viewModelScope.launch {
            while (true) {
                delay(6000L)
                meshManager.refreshRadioState()
                processStoreAndForwardQueue()
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _authError.value = "Please enter your email/username and password."
            return
        }
        viewModelScope.launch {
            val user = repository.login(identifier, password)
            if (user != null) {
                _authError.value = null
                repository.setLoggedInUserId(user.uniqueUserId)
            } else {
                _authError.value = "Invalid credentials. Try registering or Continue as Demo User."
            }
        }
    }

    fun register(name: String, phone: String, emailOrUsername: String, password: String) {
        if (name.isBlank() || phone.isBlank() || emailOrUsername.isBlank() || password.length < 4) {
            _authError.value = "Please fill all fields (password at least 4 characters)."
            return
        }
        viewModelScope.launch {
            val result = repository.register(name, phone, emailOrUsername, password)
            result.onSuccess { user ->
                _authError.value = null
                repository.setLoggedInUserId(user.uniqueUserId)
            }.onFailure { err ->
                _authError.value = err.message ?: "Registration failed."
            }
        }
    }

    fun continueAsDemoUser() {
        viewModelScope.launch {
            _authError.value = null
            val demo = repository.getOrCreateDemoUser()
            repository.setLoggedInUserId(demo.uniqueUserId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.setLoggedInUserId(null)
        }
    }

    fun updateProfile(name: String, phone: String) {
        val user = currentUser.value ?: return
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            repository.updateUserProfile(user.copy(name = name.trim(), phoneNumber = phone.trim()))
        }
    }

    fun addEmergencyContact(name: String, phone: String, relation: String) {
        val uid = loggedInUserId.value ?: return
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            repository.addEmergencyContact(
                ownerUserId = uid,
                name = name,
                phone = phone,
                relation = relation.ifBlank { "Emergency Contact" }
            )
        }
    }

    fun deleteEmergencyContact(contactId: Int) {
        viewModelScope.launch {
            repository.removeEmergencyContact(contactId)
        }
    }

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val newOffline = !isOfflineMode.value
            repository.setOfflineMode(newOffline)
            if (newOffline) {
                meshManager.stopMesh()
            } else {
                meshManager.startMesh()
                processStoreAndForwardQueue()
            }
        }
    }

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        repository.observeMessages(conversationId)

    fun markConversationRead(conversationId: String) {
        viewModelScope.launch {
            repository.clearUnread(conversationId)
        }
    }

    fun sendMessage(conversationId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val conv = repository.getConversationById(conversationId)
            val recipientId = conv?.contactDeviceId ?: "BROADCAST"
            val packetUuid = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            repository.insertMessage(
                MessageEntity(
                    messageUuid = packetUuid,
                    conversationId = conversationId,
                    recipientDeviceId = recipientId,
                    senderName = currentUser.value?.name ?: "RescueLink User",
                    content = trimmed,
                    timestamp = now,
                    isFromMe = true,
                    status = DeliveryStatus.WAITING_FOR_NEARBY,
                    priority = MessagePriority.NORMAL,
                    retryCount = 0,
                    hopPath = "Queued locally"
                )
            )
            processStoreAndForwardQueue()
        }
    }

    fun sendSosAlert(
        category: String,
        priority: String,
        description: String,
        locationLabel: String,
        coordinates: String
    ) {
        viewModelScope.launch {
            val packetUuid = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val priorityRank = MessagePriority.fromSosPriority(priority)
            val initialAlert = SosAlertEntity(
                messageUuid = packetUuid,
                senderId = currentUser.value?.uniqueUserId ?: "RL-LOCAL",
                senderName = currentUser.value?.name ?: "RescueLink User",
                isFromMe = true,
                category = category,
                priority = priority,
                priorityRank = priorityRank,
                description = description.ifBlank { "$category emergency — immediate assistance requested." },
                locationLabel = locationLabel,
                coordinates = coordinates,
                timestamp = now,
                deliveryStatus = DeliveryStatus.WAITING_FOR_NEARBY,
                retryCount = 0,
                hopPath = "Queued locally"
            )
            val alertId = repository.insertSosAlert(initialAlert).toInt()
            _activeSosProgress.value = initialAlert.copy(id = alertId)
            processStoreAndForwardQueue()
        }
    }

    /**
     * Real BLE store-and-forward queue processor:
     * - Prioritizes pending SOS alerts ahead of ordinary messages.
     * - Transfers only over real discovered BLE peers.
     * - Marks Sent only when BLE GATT write succeeds, and Delivered ONLY on real BLE ACK.
     * - Never fakes delivery or gateway responses when no BLE peer is available.
     */
    private suspend fun processStoreAndForwardQueue() {
        storeForwardMutex.withLock {
            if (isOfflineMode.value || bleRadioState.value != BleRadioState.READY) {
                return
            }
            val activePeers = discoveredPeers.value.filter {
                it.connectionState != com.example.data.mesh.BleConnectionState.DISCONNECTED
            }
            if (activePeers.isEmpty()) {
                return
            }

            val localSenderId = localPersistentDeviceId
            val localSenderName = currentUser.value?.name ?: "RescueLink User"

            // 1. SOS Alerts first (ordered by priorityRank DESC, timestamp ASC)
            val pendingSos = repository.getPendingSosAlerts()
            for (alert in pendingSos) {
                val payload = BlePacketPayload(
                    id = alert.messageUuid,
                    type = "SOS",
                    senderId = localSenderId,
                    senderName = localSenderName,
                    recipientId = "BROADCAST",
                    priority = alert.priorityRank,
                    ttl = 4,
                    timestamp = alert.timestamp,
                    body = alert.description,
                    category = alert.category,
                    location = alert.locationLabel
                )
                val targetPeer = activePeers.first()
                val result = meshManager.sendPacketToPeer(targetPeer.bleAddress, payload)
                val nextRetry = alert.retryCount + 1

                // BLE ACK confirms only that the next phone received this hop.
                // It does NOT prove that the final destination received the SOS.
                val newStatus = when {
                    result.ackReceived || result.transferred -> DeliveryStatus.FORWARDING
                    else -> DeliveryStatus.WAITING_FOR_NEARBY
                }
                val newHopPath = when {
                    result.ackReceived -> "Next hop ${targetPeer.name} acknowledged; routing onward"
                    result.transferred -> "Transferred to next hop ${targetPeer.name}"
                    else -> "Queued locally (retry #$nextRetry)"
                }
                repository.updateSosAlertDelivery(alert.id, newStatus, nextRetry, newHopPath)
                if (_activeSosProgress.value?.id == alert.id) {
                    _activeSosProgress.value = alert.copy(
                        deliveryStatus = newStatus,
                        retryCount = nextRetry,
                        hopPath = newHopPath
                    )
                }
            }

            // 2. Ordinary Chat Messages
            val pendingMessages = repository.getPendingOutboundMessages()
            for (msg in pendingMessages) {
                val payload = BlePacketPayload(
                    id = msg.messageUuid,
                    type = "MSG",
                    senderId = localSenderId,
                    senderName = localSenderName,
                    recipientId = msg.recipientDeviceId,
                    priority = msg.priority,
                    ttl = 3,
                    timestamp = msg.timestamp,
                    body = msg.content
                )
                val targetPeer = activePeers.firstOrNull {
                    it.id == msg.recipientDeviceId || it.bleAddress == msg.recipientDeviceId
                } ?: activePeers.first()
                val result = meshManager.sendPacketToPeer(targetPeer.bleAddress, payload)
                val nextRetry = msg.retryCount + 1

                // A BLE/GATT ACK is a hop acknowledgement, not an end-to-end
                // delivery receipt. Keep the message in forwarding state until a
                // destination-level ACK protocol is added.
                val newStatus = when {
                    result.ackReceived || result.transferred -> DeliveryStatus.FORWARDING
                    else -> DeliveryStatus.WAITING_FOR_NEARBY
                }
                val newHopPath = when {
                    result.ackReceived -> "Next hop ${targetPeer.name} acknowledged; routing onward"
                    result.transferred -> "Transferred to next hop ${targetPeer.name}"
                    else -> "Queued locally (retry #$nextRetry)"
                }
                repository.updateMessageDelivery(msg.id, newStatus, nextRetry, newHopPath)
            }
        }
    }
}
