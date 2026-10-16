package com.medassist.sync

import android.content.Context
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medassist.MediReminderApp
import com.medassist.data.local.Medication
import kotlinx.coroutines.tasks.await

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MediReminderApp
        val medicationDao = app.database.medicationDao()
        val intakeLogDao = app.database.intakeLogDao()
        val firestore = FirebaseFirestore.getInstance()
        val userId = inputData.getString("userId") ?: FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()

        return try {
            // 1. Download Phase: Fetch all medications from Firestore
            val snapshot = firestore.collection("users").document(userId)
                .collection("medications").get().await()
            
            val remoteMeds = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Medication::class.java)?.copy(userId = userId, syncStatus = "SYNCED")
            }
            if (remoteMeds.isNotEmpty()) {
                medicationDao.insertAll(remoteMeds)
            }

            // 2. Upload Phase: Sync Pending Medications
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
