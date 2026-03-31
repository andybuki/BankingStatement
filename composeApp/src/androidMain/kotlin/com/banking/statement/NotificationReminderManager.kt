package com.banking.statement

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Manages notification reminders for the app.
 *
 * Sends at most one notification per trigger, covering three scenarios:
 * 1. User hasn't imported a statement in 30+ days
 * 2. Monthly spending report is ready (1st of the month)
 * 3. User hasn't opened the app in 14+ days
 */
object NotificationReminderManager {

    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CODE = 2001
    private const val ACTION_CHECK_REMINDERS = "com.banking.statement.CHECK_REMINDERS"

    private const val DAYS_SINCE_IMPORT_THRESHOLD = 30
    private const val DAYS_SINCE_APP_OPEN_THRESHOLD = 14
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_CHECK_REMINDERS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule a repeating check every 24 hours
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_CHECK_REMINDERS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminders),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminders_description)
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun checkAndNotify(context: Context) {
        val prefs = AppPreferences(context)

        if (!prefs.areRemindersEnabled()) return

        createNotificationChannel(context)

        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        // Check if user hasn't opened the app in 14+ days
        val lastAppOpen = prefs.getLastAppOpenTime()
        if (lastAppOpen > 0 && (now - lastAppOpen) > DAYS_SINCE_APP_OPEN_THRESHOLD * dayMs) {
            showNotification(
                context,
                context.getString(R.string.app_name),
                context.getString(R.string.reminder_miss_you)
            )
            return
        }

        // Check if it's the 1st of the month → monthly report ready
        val calendar = java.util.Calendar.getInstance()
        if (calendar.get(java.util.Calendar.DAY_OF_MONTH) == 1) {
            showNotification(
                context,
                context.getString(R.string.app_name),
                context.getString(R.string.reminder_monthly_report)
            )
            return
        }

        // Check last import date (requires DB access via Koin)
        try {
            val repository = org.koin.java.KoinJavaComponent.getKoin()
                .get<com.banking.statement.db.TransactionRepository>()
            val latestImport = repository.getLatestImportDate()
            if (latestImport != null && latestImport > 0) {
                val importTimeMs = latestImport * 1000L // stored as Unix seconds
                if ((now - importTimeMs) > DAYS_SINCE_IMPORT_THRESHOLD * dayMs) {
                    showNotification(
                        context,
                        context.getString(R.string.app_name),
                        context.getString(R.string.reminder_no_import)
                    )
                    return
                }
            }
        } catch (_: Exception) {
            // Koin may not be initialized if app was killed; skip DB check
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

/**
 * BroadcastReceiver that triggers reminder checks.
 * Also re-schedules alarms after device reboot.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule reminders after reboot if enabled
                val prefs = AppPreferences(context)
                if (prefs.areRemindersEnabled()) {
                    NotificationReminderManager.schedule(context)
                }
            }
            else -> {
                NotificationReminderManager.checkAndNotify(context)
            }
        }
    }
}
