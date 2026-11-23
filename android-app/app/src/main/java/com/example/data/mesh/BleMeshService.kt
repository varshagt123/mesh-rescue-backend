package com.example.data.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

/**
 * Foreground service keeping RescueLink's BLE advertising and scanning active
 * while the app is open or backgrounded.
 */
class BleMeshService : Service() {

    companion object {
        private const val TAG = "RescueLinkBLE"
        private const val CHANNEL_ID = "rescuelink_ble_mesh"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, BleMeshService::class.java)
            try {
                Log.i(TAG, "[Service] Requesting BleMeshService start...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Service] Failed to start BleMeshService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            Log.i(TAG, "[Service] Stopping BleMeshService")
            context.stopService(Intent(context, BleMeshService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "[Service] BleMeshService onCreate()")
        createNotificationChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "[Service] BleMeshService entered foreground successfully")
        } catch (se: SecurityException) {
            Log.e(TAG, "[Service] SecurityException entering foreground: ${se.message}", se)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "[Service] Exception entering foreground: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[Service] BleMeshService onStartCommand(startId=$startId)")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "[Service] BleMeshService onDestroy()")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RescueLink Offline Messaging",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Bluetooth Low Energy discovery active for offline messaging."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RescueLink Offline Mesh Active")
            .setContentText("Listening for nearby RescueLink devices via Bluetooth")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
