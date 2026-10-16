package com.medassist

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicineId = intent.getIntExtra("MEDICINE_ID", -1)
        val userId = intent.getStringExtra("USER_ID") ?: return
        if (medicineId == -1) return

        val app = context.applicationContext as MediReminderApp
        val medicationDao = app.database.medicationDao()

        CoroutineScope(Dispatchers.IO).launch {
            val medication = medicationDao.getMedicationById(medicineId, userId) ?: return@launch

            // Launch full-screen intent
            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("MEDICINE_ID", medicineId)
                putExtra("USER_ID", userId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                medicineId,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "medassist_alarm_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Medicine Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Full screen medicine reminders"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Medicine Reminder")
                .setContentText("Time to take ${medication.name}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(medicineId, notification)
        }
    }
}
