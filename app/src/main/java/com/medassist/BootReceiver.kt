package com.medassist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as MediReminderApp
            val medicationDao = app.database.medicationDao()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
            
            CoroutineScope(Dispatchers.IO).launch {
                AlarmScheduler.scheduleAllFromDb(context, medicationDao, userId)
            }
        }
    }
}
