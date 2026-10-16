package com.medassist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medassist.data.local.Medication
import com.medassist.data.local.MedicationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object AlarmScheduler {

    fun scheduleNextAlarm(context: Context, medication: Medication) {
        val nextTime = getNextAlarmTime(medication)
        if (nextTime != null) {
            schedule(context, medication.id, medication.userId, nextTime)
        }
    }

    private fun schedule(context: Context, medicineId: Int, userId: String, nextTime: Long, isSnooze: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medassist.ALARM_TRIGGER"
            putExtra("MEDICINE_ID", medicineId)
            putExtra("USER_ID", userId)
            if (isSnooze) {
                putExtra("IS_SNOOZE", true)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicineId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
            }
            Log.d("AlarmScheduler", "Scheduled alarm for Med $medicineId at $nextTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException scheduling alarm", e)
        }
    }

    fun cancelAlarm(context: Context, medicineId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medassist.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicineId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Cancelled alarm for Med $medicineId")
        }
    }

    private fun getNextAlarmTime(medication: Medication): Long? {
        val timeStr = medication.time24.ifEmpty { null } ?: return null
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null

        val now = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (targetCal.before(now)) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return targetCal.timeInMillis
    }

    fun snoozeAlarm(context: Context, medicineId: Int, userId: String, minutes: Int) {
        val nextTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        schedule(context, medicineId, userId, nextTime, isSnooze = true)
    }

    fun scheduleAll(context: Context, userId: String = "local_user") {
        val app = context.applicationContext as MediReminderApp
        val medicationDao = app.database.medicationDao()
        CoroutineScope(Dispatchers.IO).launch {
            val medications = medicationDao.getAllMedications(userId).first()
            for (med in medications) {
                scheduleNextAlarm(context, med)
            }
        }
    }

    suspend fun scheduleAllFromDb(context: Context, medicationDao: MedicationDao, userId: String) {
        val medications = medicationDao.getAllMedications(userId).first()
        for (med in medications) {
            scheduleNextAlarm(context, med)
        }
    }
}
