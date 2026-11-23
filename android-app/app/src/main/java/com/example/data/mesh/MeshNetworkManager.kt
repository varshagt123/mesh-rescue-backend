package com.example.data.mesh

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class MeshNodeRole {
    PEER
}

enum class BleConnectionState(val label: String) {
    DISCOVERED("Discovered"),
    GATT_CONNECTED("GATT Connected"),
    DISCONNECTED("Disconnected")
}

data class MeshDevice(
    val id: String,          // Persistent RescueLink Device ID (e.g. RL-7A3F)
    val bleAddress: String,  // Latest BLE MAC address for GATT transport
    val name: String,        // RescueLink profile display name (e.g. Rescue_A)
    val roleLabel: String,
    val role: MeshNodeRole,
    val signalStrengthDbm: Int,
    val isConnected: Boolean,
    val connectionState: BleConnectionState = BleConnectionState.DISCOVERED
)

/**
 * Coordinates real Bluetooth Low Energy (BLE) peer discovery, GATT Server,
 * GATT Client transfers, and foreground service lifecycle.
 * No hardcoded devices or simulated responses.
 */
class MeshNetworkManager(
    private val context: Context
) {
    private val bleTransport = BleMeshTransport(context)

    val radioState: StateFlow<BleRadioState> = bleTransport.radioState
    val discoveredDevices: StateFlow<List<MeshDevice>> = bleTransport.discoveredPeers
    val incomingPackets: SharedFlow<IncomingBlePacket> = bleTransport.incomingPackets

    fun requiredPermissions(): Array<String> = bleTransport.requiredPermissions()

    fun hasRequiredPermissions(): Boolean = bleTransport.hasRequiredPermissions()

    fun updateLocalIdentity(deviceId: String, displayName: String) {
        bleTransport.updateLocalIdentity(deviceId, displayName)
    }

    fun startMesh() {
        bleTransport.refreshState()
        if (bleTransport.isRadioAvailable()) {
            BleMeshService.start(context)
            bleTransport.start()
        }
    }

    fun stopMesh() {
        bleTransport.stop()
        BleMeshService.stop(context)
    }

    fun refreshRadioState() {
        bleTransport.refreshState()
        if (bleTransport.isRadioAvailable()) {
            bleTransport.start()
        }
    }

    suspend fun sendPacketToPeer(
        deviceAddress: String,
        payload: BlePacketPayload
    ): BleTransferResult {
        return bleTransport.sendPacketToPeer(deviceAddress, payload)
    }
}
