package com.medassist.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.medassist.data.local.Medication
import com.medassist.data.local.MedicationDao
import kotlinx.coroutines.tasks.await

class OcrProcessor(private val context: Context, private val medicationDao: MedicationDao) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processPrescription(imageUri: Uri, userId: String) {
        val image = InputImage.fromFilePath(context, imageUri)
        val result = recognizer.process(image).await()
        val text = result.text

        // Basic Regex Parser
        val dosageRegex = Regex("(\\d+-\\d+-\\d+)|(twice daily)|(once daily)|(\\d+\\s?mg)")
        val medicationNameRegex = Regex("(?i)(tablet|capsule|syrup|pill)\\s+([A-Z][a-z]+)")

        val matchDosage = dosageRegex.find(text)?.value ?: "Unknown"
        val matchName = medicationNameRegex.find(text)?.groupValues?.get(2) ?: "Unrecognized Med"

        val unverifiedMed = Medication(
            userId = userId,
            name = matchName,
            reason = matchDosage, // Use reason field for dosage/instructions from OCR
            frequencyType = "Everyday",
            time24 = "08:00", // Default
            stock = 0,
            isOcrScanned = true,
            isVerified = false
        )

        medicationDao.insert(unverifiedMed)
    }
}
