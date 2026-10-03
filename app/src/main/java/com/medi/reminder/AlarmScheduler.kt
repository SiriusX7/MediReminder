package com.medi.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object AlarmScheduler {
    fun scheduleNextAlarm(context: Context, medicine: Medicine) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medi.reminder.ALARM_TRIGGER"
            putExtra("MEDICINE_ID", medicine.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicine.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTime = getNextAlarmTime(medicine)
        if (nextTime != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                    } else {
                        // Fallback or request permission
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
                Log.d("AlarmScheduler", "Scheduled alarm for Med ${medicine.id} at $nextTime")
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    fun snoozeAlarm(context: Context, medicineId: Int, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medi.reminder.ALARM_TRIGGER"
            putExtra("MEDICINE_ID", medicineId)
            putExtra("IS_SNOOZE", true)
        }
        // Use a different request code for snooze or just override the main one.
        // Overriding the main one is fine since it temporarily replaces the next schedule.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicineId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
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
            Log.d("AlarmScheduler", "Snoozed alarm for Med $medicineId to $nextTime")
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelAlarm(context: Context, medicineId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medi.reminder.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicineId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun getNextAlarmTime(medicine: Medicine): Long? {
        val parts = medicine.time24.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        val now = Calendar.getInstance()
        
        // Look ahead up to 1 year
        for (dayOffset in 0..365) {
            val targetCal = Calendar.getInstance()
            targetCal.add(Calendar.DAY_OF_YEAR, dayOffset)
            targetCal.set(Calendar.HOUR_OF_DAY, hour)
            targetCal.set(Calendar.MINUTE, minute)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)

            if (targetCal.before(now)) {
                // If the time today has already passed, skip today
                continue
            }

            // Check if this date is scheduled AND not yet taken on this date
            if (isMedicineScheduledOnDate(medicine, targetCal)) {
                if (!medicine.isTakenOn(targetCal)) {
                    return targetCal.timeInMillis
                }
            }
        }
        return null
    }

    fun scheduleAll(context: Context) {
        val dm = DataManager(context)
        val medicines = dm.loadMedicines()
        for (m in medicines) {
            scheduleNextAlarm(context, m)
        }
    }
}
