package com.example.data.mesh

import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.UUID

/**
 * RescueLink BLE GATT Service & Characteristic UUIDs and wire framing protocol.
 * Supports fragmentation and reassembly for payloads exceeding BLE ATT MTU limits.
 */
object BleProtocol {
    // Dedicated RescueLink Mesh BLE Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("0000F1D0-0000-1000-8000-00805F9B34FB")

    // Writable characteristic on GATT Server for receiving fragmented mesh frames
    val RX_CHAR_UUID: UUID = UUID.fromString("0000F1D1-0000-1000-8000-00805F9B34FB")

    // Notify characteristic on GATT Server for sending real delivery ACKs back to sender
    val TX_ACK_CHAR_UUID: UUID = UUID.fromString("0000F1D2-0000-1000-8000-00805F9B34FB")

    // Standard Client Characteristic Configuration Descriptor (CCCD) for enabling notifications
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    const val HEADER_SIZE_BYTES = 6
    const val DEFAULT_ATT_MTU = 23 // 20 bytes usable attribute value
    const val REQUESTED_ATT_MTU = 247
    private const val MAX_SERVICE_DATA_BYTES = 24

    /**
     * Encodes persistent deviceId and profile display name into compact BLE Service Data:
     * "<deviceId>|<profileName>" truncated safely to fit inside BLE Scan Response (31 bytes max).
     */
    fun encodeIdentityServiceData(deviceId: String, profileName: String): ByteArray {
        val cleanId = deviceId.replace("|", "").trim().take(8)
        val cleanName = profileName.replace("|", "").trim().ifBlank { "RescueLink" }
        val prefix = "$cleanId|"
        val maxNameBytes = (MAX_SERVICE_DATA_BYTES - prefix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
        val truncatedName = cleanName.take(maxNameBytes)
        return "$prefix$truncatedName".toByteArray(Charsets.UTF_8).take(MAX_SERVICE_DATA_BYTES).toByteArray()
    }

    /**
     * Decodes BLE Service Data back into (deviceId, profileName).
     */
    fun decodeIdentityServiceData(bytes: ByteArray?): Pair<String, String>? {
        if (bytes == null || bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8).trim()
        val sep = text.indexOf('|')
        if (sep <= 0 || sep >= text.length - 1) return null
        val id = text.substring(0, sep).trim()
        val name = text.substring(sep + 1).trim()
        if (id.isEmpty() || name.isEmpty()) return null
        return id to name
    }
}

data class BlePacketPayload(
    val id: String,
    val type: String, // "MSG", "SOS", "ACK"
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val priority: Int,
    val ttl: Int,
    val timestamp: Long,
    val body: String,
    val category: String = "",
    val location: String = ""
) {
    fun toJsonBytes(): ByteArray {
        val json = JSONObject().apply {
            put("id", id)
            put("type", type)
            put("sId", senderId)
            put("sName", senderName)
            put("rId", recipientId)
            put("pri", priority)
            put("ttl", ttl)
            put("ts", timestamp)
            put("body", body)
            if (category.isNotEmpty()) put("cat", category)
            if (location.isNotEmpty()) put("loc", location)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): BlePacketPayload? {
            return try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                BlePacketPayload(
                    id = json.getString("id"),
                    type = json.getString("type"),
                    senderId = json.optString("sId", "UNKNOWN"),
                    senderName = json.optString("sName", "Nearby User"),
                    recipientId = json.optString("rId", "BROADCAST"),
                    priority = json.optInt("pri", 20),
                    ttl = json.optInt("ttl", 3),
                    timestamp = json.optLong("ts", System.currentTimeMillis()),
                    body = json.optString("body", ""),
                    category = json.optString("cat", ""),
                    location = json.optString("loc", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Splits a serialized payload into BLE-safe frames with a 6-byte header:
 * [0..1] groupId (Short)
 * [2]    chunkIndex (UByte)
 * [3]    totalChunks (UByte)
 * [4..5] payloadLength (Short)
 */
object BleFrameCodec {
    fun fragment(packetId: String, payload: ByteArray, attMtu: Int): List<ByteArray> {
        val maxFrameBytes = (attMtu - 3).coerceAtLeast(20)
        val maxDataPerChunk = (maxFrameBytes - BleProtocol.HEADER_SIZE_BYTES).coerceAtLeast(10)
        val totalChunks = ((payload.size + maxDataPerChunk - 1) / maxDataPerChunk).coerceAtLeast(1)
        val groupId = (packetId.hashCode() and 0xFFFF).toShort()

        val frames = ArrayList<ByteArray>(totalChunks)
        for (i in 0 until totalChunks) {
            val start = i * maxDataPerChunk
            val end = minOf(start + maxDataPerChunk, payload.size)
            val chunkLen = end - start
            val buffer = ByteBuffer.allocate(BleProtocol.HEADER_SIZE_BYTES + chunkLen)
            buffer.putShort(groupId)
            buffer.put(i.toByte())
            buffer.put(totalChunks.toByte())
            buffer.putShort(chunkLen.toShort())
            buffer.put(payload, start, chunkLen)
            frames.add(buffer.array())
        }
        return frames
    }
}

/**
 * Reassembles incoming BLE frames per (remoteAddress, groupId).
 */
class BleReassembler {
    private data class AssemblyBuffer(
        val totalChunks: Int,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis()
    )

    private val buffers = mutableMapOf<String, AssemblyBuffer>()

    @Synchronized
    fun onFrameReceived(remoteAddress: String, frame: ByteArray): BlePacketPayload? {
        if (frame.size < BleProtocol.HEADER_SIZE_BYTES) return null
        val bb = ByteBuffer.wrap(frame)
        val groupId = bb.short
        val chunkIndex = bb.get().toInt() and 0xFF
        val totalChunks = bb.get().toInt() and 0xFF
        val dataLen = bb.short.toInt() and 0xFFFF

        if (totalChunks <= 0 || chunkIndex >= totalChunks) return null
        if (frame.size < BleProtocol.HEADER_SIZE_BYTES + dataLen) return null

        val data = ByteArray(dataLen)
        bb.get(data)

        val key = "$remoteAddress:$groupId"
        val entry = buffers.getOrPut(key) { AssemblyBuffer(totalChunks) }
        entry.chunks[chunkIndex] = data

        // Prune stale incomplete buffers older than 30 seconds
        val now = System.currentTimeMillis()
        buffers.entries.removeAll { now - it.value.createdAt > 30_000L }

        if (entry.chunks.size == entry.totalChunks) {
            buffers.remove(key)
            val totalSize = (0 until entry.totalChunks).sumOf { entry.chunks[it]?.size ?: 0 }
            val assembled = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until entry.totalChunks) {
                val part = entry.chunks[i] ?: return null
                System.arraycopy(part, 0, assembled, offset, part.size)
                offset += part.size
            }
            return BlePacketPayload.fromJsonBytes(assembled)
        }
        return null
    }
}
