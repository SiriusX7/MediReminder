package com.medassist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: "local_user"
            
            // Using scheduleAll which correctly handles database access and coroutines
            AlarmScheduler.scheduleAll(context, userId)
        }
    }
}
