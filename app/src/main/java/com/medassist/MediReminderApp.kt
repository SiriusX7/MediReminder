package com.medassist

import android.app.Application
import androidx.room.Room
import com.medassist.data.local.MediReminderDatabase

class MediReminderApp : Application() {
    
    val database: MediReminderDatabase by lazy {
        Room.databaseBuilder(
            this,
            MediReminderDatabase::class.java,
            "medi_reminder_db"
        )
        .fallbackToDestructiveMigration() // Useful for development; remove/configure for production
        .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize other components if needed
    }
}
