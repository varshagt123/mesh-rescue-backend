package com.example.data.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

enum class BleRadioState {
    READY,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    UNAVAILABLE
}

data class IncomingBlePacket(
    val sourceAddress: String,
    val payload: BlePacketPayload
)

data class BleTransferResult(
    val transferred: Boolean,
    val ackReceived: Boolean
)

/**
 * Real Android Bluetooth Low Energy (BLE) transport implementing:
 * - BLE Advertising with RescueLink Service UUID
 * - BLE Scanning for nearby RescueLink devices (with software UUID matching for OEM reliability)
 * - BLE GATT Server for receiving fragmented packets and sending real ACKs
 * - BLE GATT Client for connecting, negotiating MTU, writing fragmented frames, and awaiting ACKs
 */
class BleMeshTransport(
    private val context: Context
) : MeshTransport {

    companion object {
        private const val TAG = "RescueLinkBLE"
        private const val TRANSFER_TIMEOUT_MS = 12_000L
        private const val PEER_STALE_MS = 35_000L
    }

    override val mode: TransportMode = TransportMode.BLE_WIFI_DIRECT

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false
    private var isScanning = false

    private val reassembler = BleReassembler()

    private val _radioState = MutableStateFlow(evaluateRadioState())
    val radioState: StateFlow<BleRadioState> = _radioState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<MeshDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<MeshDevice>> = _discoveredPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingBlePacket>(extraBufferCapacity = 32)
    val incomingPackets: SharedFlow<IncomingBlePacket> = _incomingPackets.asSharedFlow()

    private var localDeviceId: String = "RL-0000"
    private var localDisplayName: String = "RescueLink"

    // Keyed by persistent deviceId (not rotating BLE MAC address) to prevent duplicates
    private val peerLastSeenById = mutableMapOf<String, Long>()
    private val addressToDeviceId = mutableMapOf<String, String>()
    private val gattConnectedDeviceIds = mutableSetOf<String>()
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()

    @SuppressLint("MissingPermission")
    fun updateLocalIdentity(deviceId: String, displayName: String) {
        val cleanId = deviceId.trim().ifBlank { "RL-0000" }
        val cleanName = displayName.trim().ifBlank { "RescueLink" }
        if (cleanId == localDeviceId && cleanName == localDisplayName) return
        localDeviceId = cleanId
        localDisplayName = cleanName
        Log.i(TAG, "[Identity] Updated local BLE identity: id=$localDeviceId, name=$localDisplayName")
        if (isAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {
            }
            isAdvertising = false
            startAdvertising()
        }
    }

    override fun isRadioAvailable(): Boolean {
        return evaluateRadioState() == BleRadioState.READY
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun evaluateRadioState(): BleRadioState {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "[Radio] Device does not report FEATURE_BLUETOOTH_LE")
            return BleRadioState.UNAVAILABLE
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.w(TAG, "[Radio] BluetoothAdapter is null")
            return BleRadioState.UNAVAILABLE
        }
        if (!hasRequiredPermissions()) {
            val missing = requiredPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            Log.w(TAG, "[Radio] Missing BLE permissions: $missing")
            return BleRadioState.PERMISSION_REQUIRED
        }
        return if (adapter.isEnabled) {
            BleRadioState.READY
        } else {
            Log.w(TAG, "[Radio] BluetoothAdapter is disabled")
            BleRadioState.BLUETOOTH_DISABLED
        }
    }

    fun refreshState() {
        val state = evaluateRadioState()
        _radioState.value = state
        if (state != BleRadioState.READY) {
            // Reset flags so re-enabling Bluetooth cleanly restarts advertising, scanning, and GATT server
            isAdvertising = false
            isScanning = false
            gattServer = null
        }
        pruneStalePeers()
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val state = evaluateRadioState()
        _radioState.value = state
        if (state != BleRadioState.READY) {
            Log.w(TAG, "[Start] Skipping BLE start because radioState=$state")
            return
        }

        try {
            Log.i(TAG, "[Start] Starting BLE GATT Server, Advertising, and Scanning...")
            startGattServer()
            startAdvertising()
            startScanning()
        } catch (se: SecurityException) {
            Log.e(TAG, "[Start] SecurityException starting BLE: ${se.message}", se)
            _radioState.value = BleRadioState.PERMISSION_REQUIRED
        } catch (e: Exception) {
            Log.e(TAG, "[Start] Unexpected error starting BLE: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        Log.i(TAG, "[Stop] Stopping BLE scanning, advertising, and GATT server")
        try {
            if (isScanning) {
                scanner?.stopScan(scanCallback)
                isScanning = false
            }
            if (isAdvertising) {
                advertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            gattServer?.close()
            gattServer = null
            subscribedDevices.clear()
        } catch (e: Exception) {
            Log.w(TAG, "[Stop] Error stopping BLE: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null) {
            Log.d(TAG, "[GATT Server] Already open")
            return
        }
        val manager = bluetoothManager
        if (manager == null) {
            Log.e(TAG, "[GATT Server] BluetoothManager is null")
            return
        }
        val server = manager.openGattServer(context, gattServerCallback)
        if (server == null) {
            Log.e(TAG, "[GATT Server] openGattServer() returned null")
            return
        }

        val service = BluetoothGattService(
            BleProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val rxChar = BluetoothGattCharacteristic(
            BleProtocol.RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val txAckChar = BluetoothGattCharacteristic(
            BleProtocol.TX_ACK_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            BleProtocol.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        txAckChar.addDescriptor(cccd)

        service.addCharacteristic(rxChar)
        service.addCharacteristic(txAckChar)
        val added = server.addService(service)
        gattServer = server
        Log.i(TAG, "[GATT Server] Opened and addService(${BleProtocol.SERVICE_UUID})=$added")
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (isAdvertising) {
            Log.d(TAG, "[Advertise] Already advertising")
            return
        }
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isMultipleAdvertisementSupported) {
            Log.w(TAG, "[Advertise] Device reports isMultipleAdvertisementSupported=false; attempting anyway")
        }
        val adv = adapter.bluetoothLeAdvertiser
        if (adv == null) {
            Log.e(TAG, "[Advertise] bluetoothLeAdvertiser is null (BLE peripheral mode unsupported or BT off)")
            return
        }
        advertiser = adv

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
            .build()

        val identityBytes = BleProtocol.encodeIdentityServiceData(localDeviceId, localDisplayName)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(BleProtocol.SERVICE_UUID), identityBytes)
            .build()

        Log.i(
            TAG,
            "[Advertise] Calling startAdvertising() UUID=${BleProtocol.SERVICE_UUID}, identity=$localDeviceId|$localDisplayName (${identityBytes.size}B)"
        )
        adv.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (isScanning) {
            Log.d(TAG, "[Scan] Already scanning")
            return
        }
        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            Log.e(TAG, "[Scan] bluetoothLeScanner is null")
            return
        }
        scanner = leScanner

        // Use software UUID filtering in handleScanResult() rather than hardware ScanFilter,
        // because several Android OEM BLE chipsets drop 128-bit/16-bit Service UUID hardware filters.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        Log.i(TAG, "[Scan] Calling startScan() (filtering for RescueLink UUID=${BleProtocol.SERVICE_UUID})")
        leScanner.startScan(null, settings, scanCallback)
        isScanning = true
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.i(TAG, "[Advertise] SUCCESS — Advertising RescueLink UUID=${BleProtocol.SERVICE_UUID}")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "CODE_$errorCode"
            }
            Log.e(TAG, "[Advertise] FAILED — reason=$reason (code=$errorCode)")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e(TAG, "[Scan] FAILED — errorCode=$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val serviceParcelUuid = ParcelUuid(BleProtocol.SERVICE_UUID)
        val serviceDataBytes = scanRecord.getServiceData(serviceParcelUuid)
        val hasServiceUuid = scanRecord.serviceUuids?.any { it.uuid == BleProtocol.SERVICE_UUID } == true

        // Strictly ignore unrelated Bluetooth devices
        if (!hasServiceUuid && serviceDataBytes == null) return

        val device = result.device ?: return
        val address = device.address ?: return

        val decoded = BleProtocol.decodeIdentityServiceData(serviceDataBytes)
        // If we haven't received the ScanResponse with identity yet, check if we already mapped this BLE address
        val persistentId = decoded?.first ?: addressToDeviceId[address]
        if (persistentId == null) {
            // Wait for active ScanResponse containing RescueLink service data rather than showing raw MAC
            return
        }
        if (persistentId == localDeviceId) return // Ignore self

        val profileName = decoded?.second
            ?: _discoveredPeers.value.firstOrNull { it.id == persistentId }?.name
            ?: "RescueLink Device"

        addressToDeviceId[address] = persistentId
        val isNewPeer = !peerLastSeenById.containsKey(persistentId)
        peerLastSeenById[persistentId] = System.currentTimeMillis()

        if (isNewPeer) {
            Log.i(TAG, "[Scan] DISCOVERED RescueLink peer: name=$profileName, id=$persistentId, addr=$address, rssi=${result.rssi} dBm")
        }

        val isGattConnected = gattConnectedDeviceIds.contains(persistentId)
        val peer = MeshDevice(
            id = persistentId,
            bleAddress = address,
            name = profileName,
            roleLabel = "Nearby BLE Device",
            role = MeshNodeRole.PEER,
            signalStrengthDbm = result.rssi,
            isConnected = true,
            connectionState = if (isGattConnected) BleConnectionState.GATT_CONNECTED else BleConnectionState.DISCOVERED
        )

        _discoveredPeers.update { current ->
            val without = current.filterNot { it.id == persistentId }
            (without + peer).sortedByDescending { it.signalStrengthDbm }
        }
    }

    private fun updatePeerGattState(address: String, gattConnected: Boolean) {
        val deviceId = addressToDeviceId[address] ?: return
        if (gattConnected) {
            gattConnectedDeviceIds.add(deviceId)
        } else {
            gattConnectedDeviceIds.remove(deviceId)
        }
        _discoveredPeers.update { list ->
            list.map { peer ->
                if (peer.id == deviceId) {
                    val stillSeen = peerLastSeenById.containsKey(deviceId)
                    peer.copy(
                        bleAddress = address,
                        connectionState = when {
                            gattConnected -> BleConnectionState.GATT_CONNECTED
                            stillSeen -> BleConnectionState.DISCOVERED
                            else -> BleConnectionState.DISCONNECTED
                        }
                    )
                } else {
                    peer
                }
            }
        }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        val staleIds = peerLastSeenById.filterValues { now - it > PEER_STALE_MS }.keys
            .filterNot { it in gattConnectedDeviceIds }
        if (staleIds.isNotEmpty()) {
            Log.i(TAG, "[Scan] Marking stale peers disconnected: $staleIds")
            staleIds.forEach { peerLastSeenById.remove(it) }
            _discoveredPeers.update { list ->
                list.map { peer ->
                    if (peer.id in staleIds) {
                        peer.copy(isConnected = false, connectionState = BleConnectionState.DISCONNECTED)
                    } else {
                        peer
                    }
                }
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "[GATT Server] Connection state change: addr=${device.address}, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updatePeerGattState(device.address, true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedDevices.remove(device)
                updatePeerGattState(device.address, false)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == BleProtocol.CCCD_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    subscribedDevices.add(device)
                } else {
                    subscribedDevices.remove(device)
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            if (characteristic.uuid == BleProtocol.RX_CHAR_UUID) {
                val assembled = reassembler.onFrameReceived(device.address, value)
                if (assembled != null) {
                    Log.i(TAG, "[GATT Server] Reassembled packet id=${assembled.id} type=${assembled.type} from ${device.address}")
                    _incomingPackets.tryEmit(IncomingBlePacket(device.address, assembled))
                    if (assembled.type != "ACK") {
                        sendAckNotification(device, assembled.id)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendAckNotification(device: BluetoothDevice, ackPacketId: String) {
        val server = gattServer ?: return
        val service = server.getService(BleProtocol.SERVICE_UUID) ?: return
        val txChar = service.getCharacteristic(BleProtocol.TX_ACK_CHAR_UUID) ?: return
        val ackBytes = "ACK:$ackPacketId".toByteArray(Charsets.UTF_8)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, txChar, false, ackBytes)
            } else {
                @Suppress("DEPRECATION")
                txChar.value = ackBytes
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, txChar, false)
            }
            Log.i(TAG, "[GATT Server] Sent ACK for packet=$ackPacketId to ${device.address}")
        } catch (e: Exception) {
            Log.w(TAG, "[GATT Server] Failed to send ACK notification: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendPacketToPeer(deviceAddress: String, payload: BlePacketPayload): BleTransferResult {
        if (evaluateRadioState() != BleRadioState.READY) {
            return BleTransferResult(transferred = false, ackReceived = false)
        }
        val remoteDevice = try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return BleTransferResult(transferred = false, ackReceived = false)

        Log.i(TAG, "[GATT Client] Connecting to $deviceAddress to send packet id=${payload.id}")
        val resultDeferred = CompletableDeferred<BleTransferResult>()
        val serialized = payload.toJsonBytes()

        var negotiatedMtu = BleProtocol.DEFAULT_ATT_MTU
        var frames: List<ByteArray> = emptyList()
        var currentFrameIndex = 0
        var rxChar: BluetoothGattCharacteristic? = null
        var allFramesWritten = false
        var gattRef: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "[GATT Client] Connection failed status=$status")
                    updatePeerGattState(deviceAddress, false)
                    resultDeferred.complete(BleTransferResult(allFramesWritten, false))
                    gatt.close()
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "[GATT Client] Connected to $deviceAddress, requesting MTU")
                    updatePeerGattState(deviceAddress, true)
                    gatt.requestMtu(BleProtocol.REQUESTED_ATT_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    updatePeerGattState(deviceAddress, false)
                    resultDeferred.complete(BleTransferResult(allFramesWritten, false))
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    negotiatedMtu = mtu
                }
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    resultDeferred.complete(BleTransferResult(false, false))
                    gatt.disconnect()
                    return
                }
                val service = gatt.getService(BleProtocol.SERVICE_UUID)
                rxChar = service?.getCharacteristic(BleProtocol.RX_CHAR_UUID)
                val txAckChar = service?.getCharacteristic(BleProtocol.TX_ACK_CHAR_UUID)
                if (rxChar == null) {
                    Log.w(TAG, "[GATT Client] RescueLink RX characteristic not found on $deviceAddress")
                    resultDeferred.complete(BleTransferResult(false, false))
                    gatt.disconnect()
                    return
                }
                if (txAckChar != null) {
                    gatt.setCharacteristicNotification(txAckChar, true)
                    val cccd = txAckChar.getDescriptor(BleProtocol.CCCD_UUID)
                    if (cccd != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
                        return
                    }
                }
                startWritingFrames(gatt)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                startWritingFrames(gatt)
            }

            private fun startWritingFrames(gatt: BluetoothGatt) {
                frames = BleFrameCodec.fragment(payload.id, serialized, negotiatedMtu)
                currentFrameIndex = 0
                Log.d(TAG, "[GATT Client] Writing ${frames.size} frame(s) (MTU=$negotiatedMtu)")
                writeNextFrame(gatt)
            }

            private fun writeNextFrame(gatt: BluetoothGatt) {
                val targetChar = rxChar ?: return
                if (currentFrameIndex >= frames.size) {
                    allFramesWritten = true
                    return
                }
                val chunk = frames[currentFrameIndex]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        targetChar,
                        chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                } else {
                    @Suppress("DEPRECATION")
                    targetChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    targetChar.value = chunk
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(targetChar)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "[GATT Client] Frame write failed status=$status")
                    resultDeferred.complete(BleTransferResult(false, false))
                    gatt.disconnect()
                    return
                }
                currentFrameIndex++
                if (currentFrameIndex < frames.size) {
                    writeNextFrame(gatt)
                } else {
                    allFramesWritten = true
                    Log.i(TAG, "[GATT Client] All ${frames.size} frame(s) written; awaiting ACK...")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleAckBytes(gatt, value)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleAckBytes(gatt, value)
            }

            private fun handleAckBytes(gatt: BluetoothGatt, value: ByteArray) {
                val text = String(value, Charsets.UTF_8)
                if (text == "ACK:${payload.id}") {
                    Log.i(TAG, "[GATT Client] Received real ACK for packet=${payload.id}")
                    resultDeferred.complete(BleTransferResult(transferred = true, ackReceived = true))
                    gatt.disconnect()
                }
            }
        }

        try {
            gattRef = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(context, false, callback)
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "[GATT Client] SecurityException on connectGatt: ${se.message}", se)
            return BleTransferResult(false, false)
        }

        val outcome = withTimeoutOrNull(TRANSFER_TIMEOUT_MS) {
            resultDeferred.await()
        } ?: BleTransferResult(transferred = allFramesWritten, ackReceived = false)

        try {
            gattRef?.close()
        } catch (_: Exception) {
        }
        Log.i(TAG, "[GATT Client] Transfer finished: transferred=${outcome.transferred}, ack=${outcome.ackReceived}")
        return outcome
    }
}
