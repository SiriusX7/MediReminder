package com.medassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

data class ScannedMedicineCandidate(
    val name: String,
    val instructions: String,
    val form: MedForm,
    val quantity: Int = 1,
    val isDatabaseVerified: Boolean = false,
    val genericSalt: String = "",
    val apiSource: String = "Local DB",
)

data class ParsedMedicineInfo(
    val name: String = "",
    val instructions: String = "",
    val quantity: Int = 1,
    val form: MedForm = MedForm.Pill,
    val rawText: String = "",
    val isValidPrescription: Boolean = false,
    val candidates: List<ScannedMedicineCandidate> = emptyList(),
)

object OcrTextRecognizer {

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): ParsedMedicineInfo? {
        // 1. Primary: Query Gemini Vision AI for near-100% accurate vision scanning
        try {
            val geminiCandidates = GeminiVisionApiClient.analyzePrescriptionImage(context, uri)
            if (geminiCandidates != null && geminiCandidates.isNotEmpty()) {
                val first = geminiCandidates.first()
                return ParsedMedicineInfo(
                    name = first.name,
                    instructions = first.instructions,
                    quantity = first.quantity,
                    form = first.form,
                    rawText = "Gemini AI Scanned Prescription",
                    isValidPrescription = true,
                    candidates = geminiCandidates
                )
            }
        } catch (e: Exception) {
            Log.e("OcrTextRecognizer", "Gemini vision recognition error: ${e.localizedMessage}")
        }

        // 2. Secondary / Offline Fallback: Local ML Kit OCR + Medicine Database
        return suspendCancellableCoroutine { continuation ->
            try {
                val bitmap = getOrientedBitmap(context, uri)
                val image = if (bitmap != null) {
                    InputImage.fromBitmap(bitmap, 0)
                } else {
                    InputImage.fromFilePath(context, uri)
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                val parsed = parsePrescriptionText(visionText)
                                if (continuation.isActive) {
                                    continuation.resume(parsed)
                                }
                            } catch (e: Exception) {
                                Log.e("OcrTextRecognizer", "Error parsing OCR text: ${e.localizedMessage}")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("OcrTextRecognizer", "ML Kit OCR process failed: ${e.localizedMessage}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            } catch (e: Exception) {
                Log.e("OcrTextRecognizer", "Local ML Kit initialization error: ${e.localizedMessage}")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun getOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val orientation = try {
                ByteArrayInputStream(bytes).use { bais ->
                    val exif = ExifInterface(bais)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            val maxDimension = 1280
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = (maxDimension.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)

            val matrix = Matrix().apply {
                if (scale < 1.0f) {
                    postScale(scale, scale)
                }
                if (rotationDegrees != 0) {
                    postRotate(rotationDegrees.toFloat())
                }
            }

            Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, true)
        } catch (e: Exception) {
            Log.e("OcrTextRecognizer", "Error loading bitmap: ${e.localizedMessage}")
            null
        }
    }

    private data class LineInfo(
        val text: String,
        val top: Int,
        val left: Int,
    )

    private suspend fun parsePrescriptionText(visionText: Text): ParsedMedicineInfo {
        val rawText = visionText.text
        if (rawText.isBlank()) return ParsedMedicineInfo(isValidPrescription = false)

        // Extract lines with bounding box positions to reconstruct row reading order
        val lineItems = visionText.textBlocks.flatMap { block ->
            block.lines
        }.mapNotNull { line ->
            val text = line.text.trim()
            val box = line.boundingBox
            if (text.isNotBlank() && box != null) {
                LineInfo(text = text, top = box.top, left = box.left)
            } else null
        }

        val sortedLines = mutableListOf<String>()
        if (lineItems.isNotEmpty()) {
            val groupedRows = lineItems.groupBy { lineInfo ->
                lineInfo.top / 24
            }.entries.sortedBy { it.key }

            for (row in groupedRows) {
                val rowText = row.value.sortedBy { it.left }.joinToString(" ") { it.text }
                if (rowText.isNotBlank()) {
                    sortedLines.add(rowText.trim())
                }
            }
        }

        val lines = if (sortedLines.isNotEmpty()) sortedLines else visionText.textBlocks.flatMap { block ->
            block.lines.map { it.text.trim() }
        }.filter { it.isNotBlank() }

        if (lines.isEmpty()) return ParsedMedicineInfo(rawText = rawText, isValidPrescription = false)

        val lowerText = rawText.lowercase()

        // Explicit non-medical terms (e.g. store receipts, invoices)
        val nonMedicalKeywords = listOf(
            "receipt", "subtotal", "total $", "amount due", "invoice", "cashier",
            "checkout", "payment method", "card ending", "visa", "mastercard",
            "thank you for shopping", "balance due"
        )

        // Medical/Prescription terms & abbreviations
        val medicalIndicators = listOf(
            "rx", "dr", "doctor", "clinic", "hospital", "patient", "pt", "pharmacy",
            "tab", "tablet", "tablets", "cap", "capsule", "capsules", "pill", "pills",
            "syr", "syrup", "syp", "susp", "suspension", "inj", "injection", "vial",
            "drop", "drops", "inhaler", "ointment", "cream", "powder", "dose", "dosage",
            "mg", "mcg", "ml", "g", "iu", "tsp", "tbsp", "take", "sig", "qty", "quantity",
            "refill", "dispense", "daily", "day", "days", "night", "morning", "evening",
            "after", "before", "meal", "food", "bedtime", "1-0-1", "0-0-1", "1-1-1", "1-0-0",
            "od", "bd", "bid", "tid", "tds", "qid", "hs", "sos", "po", "prn", "stat", "med", "medicine",
            "treatment", "limcee", "shelcal", "zincovit", "paracetamol", "montelukast", "azithromycin", "sinarest"
        )

        val containsMedicalIndicator = medicalIndicators.any { indicator ->
            if (indicator.length <= 2) {
                lowerText.contains(Regex("(?i)\\b$indicator\\b"))
            } else {
                lowerText.contains(indicator)
            }
        } || lowerText.contains(Regex("(?i)\\b\\d+\\s*(mg|mcg|ml|g|iu)\\b"))
          || lowerText.contains(Regex("\\b[0-1]-[0-1]-[0-1]\\b"))

        val isExplicitNonMedical = nonMedicalKeywords.any { lowerText.contains(it) } && !containsMedicalIndicator

        val isValidPrescription = containsMedicalIndicator || (!isExplicitNonMedical && lines.isNotEmpty())
        if (!isValidPrescription) {
            return ParsedMedicineInfo(rawText = rawText, isValidPrescription = false)
        }

        val ignoreSectionHeaderKeywords = listOf(
            "investigation", "investigations", "cbp", "crp", "ct chest", "ct scan",
            "x-ray", "ecg", "mri", "blood test", "monitoring", "pulse oxymeter",
            "warning", "spo2", "fever", "breathless", "consult", "home care",
            "treatment", "advice", "sunshine", "hospitals", "hospital", "physician",
            "date", "page", "phone", "address", "dr.", "dr", "doctor", "patient", "pt name",
            "clinic", "diagnosis", "age", "sex", "gender", "signature", "reg no", "receipt",
            "subtotal", "total", "invoice", "tel", "dengue", "antigen", "rt pcr", "rtpcr",
            "fever profile", "cbc", "esr", "cue", "widal", "sgpt", "smear", "steam inhalation",
            "inhalation", "isolate", "cold water", "hospitalised", "symptoms", "saturation",
            "lab test", "lab tests", "laboratory", "drug allergies", "allergies", "doctor's advice",
            "doctors advice", "follow up", "follow-up", "description"
        )

        val candidates = mutableListOf<ScannedMedicineCandidate>()

        val freqKeywords = listOf(
            "twice a day", "once a day", "thrice a day", "once at night",
            "once daily", "twice daily", "thrice daily", "once a week",
            "twice a week", "as per physician", "daily", "every day",
            "after meal", "before meal", "after food", "before food",
            "after meals", "before meals", "1-0-1", "0-0-1", "1-1-1", "1-0-0",
            "0-1-0", "bd", "tds", "qid", "od", "hs", "sos", "stat"
        )

        var inNonMedicineSection = false

        for (line in lines) {
            val lowerLine = line.lowercase().trim()

            // Check if we entered a non-medicine section (e.g. Lab Test, Doctor's Advice, Follow Up)
            if (lowerLine.contains("lab test") || lowerLine.contains("doctor's advice") ||
                lowerLine.contains("doctors advice") || lowerLine.contains("drug allergies") ||
                lowerLine.contains("follow up") || lowerLine.contains("description")) {
                if (!lowerLine.contains("tablet") && !lowerLine.contains("1-0-1")) {
                    inNonMedicineSection = true
                    continue
                }
            }

            // Reset section if we enter Prescribed Medicines or Treatment
            if (lowerLine.contains("prescribed medicine") || lowerLine.contains("treatment") || lowerLine.contains("rx")) {
                inNonMedicineSection = false
                continue
            }

            if (inNonMedicineSection) {
                continue
            }

            // Skip lab test lines or known header keywords
            val isIgnoredHeader = ignoreSectionHeaderKeywords.any { header ->
                lowerLine.startsWith(header) || lowerLine == header || (lowerLine.contains(" $header ") && !lowerLine.contains("mg") && !lowerLine.contains("ml"))
            }
            if (isIgnoredHeader && !lowerLine.contains("twice") && !lowerLine.contains("once") && !lowerLine.contains("thrice") && !lowerLine.contains("1-0-1")) {
                continue
            }

            // Check if line looks like a medicine entry
            val hasNumBullet = lowerLine.matches(Regex("^[0-9]+[.)]\\s*.*"))
            val hasFormPrefix = lowerLine.contains(Regex("(?i)\\b(t\\.|tab|tab\\.|cap|cap\\.|syr|syr\\.|syp|inj|inj\\.|capsule|tablet|syrup|injection)\\b"))
            val hasFrequency = freqKeywords.any { lowerLine.contains(it) }
            val hasDosageMetric = lowerLine.contains(Regex("(?i)\\b\\d+(\\.\\d+)?\\s*(mg|g|mcg|ml|iu|mEq)\\b"))

            if (hasNumBullet || hasFormPrefix || hasFrequency || hasDosageMetric) {
                // Determine Form
                val form = when {
                    lowerLine.contains("syr") || lowerLine.contains("syp") || lowerLine.contains("syrup") -> MedForm.Syrup
                    lowerLine.contains("inj") || lowerLine.contains("injection") || lowerLine.contains("vial") -> MedForm.Injection
                    lowerLine.contains("drop") -> MedForm.Drops
                    lowerLine.contains("inhaler") -> MedForm.Inhaler
                    lowerLine.contains("powder") -> MedForm.Powder
                    else -> MedForm.Pill
                }

                // Clean up prefix (e.g. "1. TABLET SINAREST 100MG" -> "SINAREST 100MG")
                var cleanedLine = line.replace(Regex("^[0-9]+[.)]\\s*"), "").trim()
                cleanedLine = cleanedLine.replace(Regex("(?i)\\b(tablets?|capsules?|syrups?|injections?|tab|cap|syr|syp|inj)\\b\\.?\\s*"), "").trim()
                cleanedLine = cleanedLine.replace(Regex("(?i)^[tcsi]\\.\\s*"), "").trim()

                var matchedInstruction = ""
                var nameOnly = cleanedLine

                for (freq in freqKeywords) {
                    val idx = nameOnly.lowercase().indexOf(freq)
                    if (idx != -1) {
                        matchedInstruction = nameOnly.substring(idx).trim()
                        nameOnly = nameOnly.substring(0, idx).trim()
                        break
                    }
                }

                nameOnly = nameOnly.trim().trimEnd('-', ',', '.', ' ', ':')

                val sanitizedName = sanitizeMedicineName(nameOnly)
                val isNameIgnored = ignoreSectionHeaderKeywords.any { it.equals(sanitizedName, ignoreCase = true) }

                // Database Fuzzy Matching Lookup
                val dbMatch = MedicineDatabase.findBestMatch(line) ?: MedicineDatabase.findBestMatch(sanitizedName)

                var finalName: String
                var finalForm: MedForm
                var isVerified: Boolean
                var genericSalt: String
                var apiSource: String

                if (dbMatch != null) {
                    if (dbMatch.isBrandMatch) {
                        val brand = dbMatch.entity.brandName
                        val str = dbMatch.matchedStrength
                        finalName = if (str.isNotEmpty() && !brand.lowercase().contains(str.lowercase())) "$brand $str" else brand
                    } else {
                        finalName = sanitizedName.ifEmpty { dbMatch.entity.genericSalt }
                    }
                    finalForm = dbMatch.entity.defaultForm
                    isVerified = true
                    genericSalt = dbMatch.entity.genericSalt
                    apiSource = "Local DB"
                } else {
                    // Query free Online APIs (OpenFDA & RxNorm) as online fallback
                    val onlineMatch = OnlineMedicineApiClient.searchOnline(sanitizedName)
                    if (onlineMatch != null) {
                        finalName = onlineMatch.candidate.name
                        finalForm = onlineMatch.candidate.form
                        isVerified = true
                        genericSalt = onlineMatch.candidate.genericSalt
                        apiSource = onlineMatch.apiSource
                    } else {
                        finalName = sanitizedName
                        finalForm = form
                        isVerified = false
                        genericSalt = ""
                        apiSource = "Local DB"
                    }
                }

                if (finalName.length >= 2 && !isNameIgnored) {
                    if (candidates.none { it.name.equals(finalName, ignoreCase = true) }) {
                        candidates.add(
                            ScannedMedicineCandidate(
                                name = finalName,
                                instructions = matchedInstruction.ifEmpty { "Take as directed" },
                                form = finalForm,
                                quantity = 1,
                                isDatabaseVerified = isVerified,
                                genericSalt = genericSalt,
                                apiSource = apiSource
                            )
                        )
                    }
                }
            }
        }

        val firstCandidate = candidates.firstOrNull()

        return ParsedMedicineInfo(
            name = firstCandidate?.name ?: "",
            instructions = firstCandidate?.instructions ?: "Take as directed",
            quantity = firstCandidate?.quantity ?: 1,
            form = firstCandidate?.form ?: MedForm.Pill,
            rawText = rawText,
            isValidPrescription = true,
            candidates = candidates,
        )
    }

    private fun sanitizeMedicineName(rawName: String): String {
        var name = rawName

        val noisePatterns = listOf(
            "(?i)\\bfilm[- ]coated\\b",
            "(?i)\\bfilm[- ]c\\b",
            "(?i)\\bdihydrate\\b",
            "(?i)\\bmonohydrate\\b",
            "(?i)\\bhydrochloride\\b",
            "(?i)\\bhcl\\b",
            "(?i)\\bip\\b",
            "(?i)\\bbp\\b",
            "(?i)\\busp\\b",
            "(?i)\\b1\\s*tab\\b",
            "(?i)\\b1\\s*cap\\b",
            "(?i)\\btablets?\\b",
            "(?i)\\bcapsules?\\b",
            "(?i)\\bsyrup\\b",
            "(?i)\\binjection\\b",
            "(?i)\\bdispersible\\b",
            "(?i)\\bsustained\\s*release\\b",
            "(?i)\\bsr\\b",
            "(?i)\\ber\\b",
            "(?i)\\bxl\\b"
        )

        for (pattern in noisePatterns) {
            name = name.replace(Regex(pattern), "")
        }

        name = name.replace(Regex("\\s+"), " ").trim().trimEnd('-', ',', '.', ' ', ':')

        return name.ifEmpty { rawName }
    }
}
