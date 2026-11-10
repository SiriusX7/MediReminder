package com.medassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object GeminiVisionApiClient {

    private val CANDIDATE_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-flash-latest"
    )

    suspend fun analyzePrescriptionImage(context: Context, imageUri: Uri): List<ScannedMedicineCandidate>? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w("GeminiVisionApi", "GEMINI_API_KEY is blank, falling back to local OCR")
            return@withContext null
        }

        val base64Image = uriToBase64Jpeg(context, imageUri) ?: return@withContext null

        val prompt = """
            You are a professional medical prescription analyzer. Analyze this doctor prescription or medicine label image carefully.
            Extract ALL prescribed medicines and return ONLY a valid raw JSON array with no extra text or markdown formatting (no ```json code blocks):
            [
              {
                "name": "Medicine Name with dosage strength (e.g. Azithromycin 250mg or Sinarest 100mg)",
                "form": "Pill",
                "instructions": "Daily: 1-0-1 After Food"
              }
            ]
            Rules:
            1. "form" MUST be one of: "Pill", "Syrup", "Injection", "Drops", "Inhaler", "Powder".
            2. "name" should be the clean brand name or generic medicine name (e.g. "Azithromycin 250mg", "Sinarest 100mg", "Dolo 650mg").
            3. "instructions" should be the prescribed dosage & frequency (e.g. "Daily: 1-0-1", "Twice a day", "Once a day").
            4. STRICTLY IGNORE: Lab tests (such as RT-PCR, Dengue Antigen, Fever Profile, CBC, ESR, CRP, CT Chest), Doctor's Advice (such as steam inhalation, isolate, drink warm water), Hospital headers, and Doctor IDs.
        """.trimIndent()

        val payload = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()

            val textPart = JSONObject().apply {
                put("text", prompt)
            }

            val imagePart = JSONObject().apply {
                val inlineData = JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                }
                put("inline_data", inlineData)
            }

            parts.put(textPart)
            parts.put(imagePart)
            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)
        }

        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)

        for (modelName in CANDIDATE_MODELS) {
            try {
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val url = URL(urlString)

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-goog-api-key", apiKey)
                    setRequestProperty("x-api-key", apiKey)
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write(payloadBytes)
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseText)

                    val candidatesArray = responseJson.optJSONArray("candidates")
                    if (candidatesArray != null && candidatesArray.length() > 0) {
                        val firstCandidate = candidatesArray.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val responseParts = content?.optJSONArray("parts")

                        if (responseParts != null && responseParts.length() > 0) {
                            var jsonText = responseParts.getJSONObject(0).optString("text").trim()

                            // Strip markdown code block ticks if present
                            if (jsonText.startsWith("```")) {
                                jsonText = jsonText.replace(Regex("^```[a-z]*\\s*"), "").replace(Regex("\\s*```$"), "").trim()
                            }

                            val startIndex = jsonText.indexOf("[")
                            val endIndex = jsonText.lastIndexOf("]")
                            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                                jsonText = jsonText.substring(startIndex, endIndex + 1)
                                val medicineArray = JSONArray(jsonText)
                                val resultList = mutableListOf<ScannedMedicineCandidate>()

                                for (i in 0 until medicineArray.length()) {
                                    val item = medicineArray.getJSONObject(i)
                                    val name = item.optString("name").trim()
                                    val formStr = item.optString("form").trim()
                                    val instructions = item.optString("instructions").trim()

                                    val form = when {
                                        formStr.contains("Syrup", ignoreCase = true) -> MedForm.Syrup
                                        formStr.contains("Injection", ignoreCase = true) -> MedForm.Injection
                                        formStr.contains("Drop", ignoreCase = true) -> MedForm.Drops
                                        formStr.contains("Inhaler", ignoreCase = true) -> MedForm.Inhaler
                                        formStr.contains("Powder", ignoreCase = true) -> MedForm.Powder
                                        else -> MedForm.Pill
                                    }

                                    if (name.isNotBlank()) {
                                        resultList.add(
                                            ScannedMedicineCandidate(
                                                name = name,
                                                instructions = instructions.ifEmpty { "Take as directed" },
                                                form = form,
                                                quantity = 1,
                                                isDatabaseVerified = true,
                                                genericSalt = "",
                                                apiSource = "Gemini AI ($modelName)"
                                            )
                                        )
                                    }
                                }

                                if (resultList.isNotEmpty()) {
                                    Log.d("GeminiVisionApi", "Gemini AI Vision ($modelName) successfully parsed ${resultList.size} medicines")
                                    return@withContext resultList
                                }
                            }
                        }
                    }
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w("GeminiVisionApi", "Gemini API ($modelName) HTTP Error $responseCode: $errText")
                }
            } catch (e: Exception) {
                Log.w("GeminiVisionApi", "Gemini API ($modelName) Exception: ${e.localizedMessage}")
            }
        }
        null
    }

    private fun uriToBase64Jpeg(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
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

            val orientedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, true)

            val outputStream = ByteArrayOutputStream()
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val outputBytes = outputStream.toByteArray()

            Base64.encodeToString(outputBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("GeminiVisionApi", "Error preparing base64 JPEG: ${e.localizedMessage}")
            null
        }
    }
}
