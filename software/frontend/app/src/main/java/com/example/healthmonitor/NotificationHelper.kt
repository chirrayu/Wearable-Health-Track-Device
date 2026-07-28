package com.example.healthmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID = "triage_ai_alerts"
    private const val CHANNEL_NAME = "Triage AI Alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical and informational alerts from Triage AI"
                enableLights(true)
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendAlertNotification(context: Context, alert: AppAlert) {

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val icon = when (alert.severity) {
            "critical" -> android.R.drawable.ic_dialog_alert
            "warning"  -> android.R.drawable.ic_dialog_info
            else       -> android.R.drawable.ic_dialog_info
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(alert.title)
            .setContentText("${alert.soldierName} (${alert.soldierSerial}) — ${alert.message}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(
                if (alert.severity == "critical") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight; silently ignore.
        }
    }
}