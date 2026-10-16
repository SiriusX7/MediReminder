package com.medassist.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medassist.MediReminderApp
import kotlinx.coroutines.tasks.await

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MediReminderApp
        val medicationDao = app.database.medicationDao()
        val intakeLogDao = app.database.intakeLogDao()
        val firestore = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()

        return try {
            // Sync Pending Medications
            val pendingMeds = medicationDao.getPendingSyncMedications(userId)
            for (med in pendingMeds) {
                firestore.collection("users").document(userId)
                    .collection("medications").document(med.id.toString())
                    .set(med).await()
                medicationDao.update(med.copy(syncStatus = "SYNCED"))
            }

            // Sync Pending Logs
            val pendingLogs = intakeLogDao.getPendingSyncLogs(userId)
            for (log in pendingLogs) {
                firestore.collection("users").document(userId)
                    .collection("intake_logs").document(log.id.toString())
                    .set(log).await()
                intakeLogDao.update(log.copy(syncStatus = "SYNCED"))
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
