package com.example.data.mesh

enum class TransportMode(val label: String) {
    BLE_WIFI_DIRECT("Bluetooth Low Energy (BLE)")
}

/**
 * Abstraction for the underlying peer-to-peer radio layer.
 * Internet is NOT required for device-to-device exchange.
 */
interface MeshTransport {
    val mode: TransportMode
    fun isRadioAvailable(): Boolean
}
