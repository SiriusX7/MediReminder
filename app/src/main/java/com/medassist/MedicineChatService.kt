package com.medassist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
)

class MedicineChatService {

    suspend fun askMedicineQuestion(query: String, activeMedicineNames: List<String>): String =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY.trim()
            val hasApiKey = apiKey.isNotBlank()

            // Clean query by removing trailing UI quantity numbers (e.g. "Dolo 650 1" -> "Dolo 650")
            val cleanQuery = sanitizeMedicineQuery(query)

            if (hasApiKey) {
                try {
                    val result = callGeminiChatApi(apiKey, cleanQuery, activeMedicineNames)
                    if (result.isNotBlank()) return@withContext result
                } catch (_: Exception) {
                    // Fall back to verified clinical knowledge base and web search on network/API failure
                }
            }

            // Fallback 1: Local verified clinical dictionary
            val localResult = getVerifiedClinicalKnowledge(cleanQuery)
            if (!localResult.startsWith("📋 Clinical Health Information")) {
                return@withContext localResult
            }

            // Fallback 2: OpenFDA / RxNorm Web API search
            val webResult = OnlineMedicineApiClient.fetchDrugClinicalDetails(cleanQuery)
            if (!webResult.isNullOrBlank()) {
                return@withContext webResult
            }

            return@withContext localResult
        }

    private fun sanitizeMedicineQuery(query: String): String {
        return query
            .replace(Regex("(?i)Can you explain clinical uses, side effects, and precautions for\\s*"), "")
            .replace(Regex("(?i)\\b(\\d+)\\s*$"), "") // Strip trailing quantity digit e.g. "Dolo 650 1" -> "Dolo 650"
            .trim(' ', '?', '.', '!')
    }

    private fun callGeminiChatApi(apiKey: String, query: String, activeMeds: List<String>): String {
        val systemPrompt = """
            You are a helpful clinical pharmacotherapy information assistant in the MedAssist app.
            Your role is educational and informational.

            MANDATORY GUIDELINES:
            1. Explain:
               - Generic and brand name details
               - Common clinical uses and associated health conditions
               - Common side effects
               - Key precautions, food/drug interactions, and missed-dose instructions
            2. NEVER diagnose patients.
            3. NEVER advise starting, stopping, altering doses, or changing medications without doctor consultation.
            4. Include a reminder at the end: "Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor or pharmacist."
        """.trimIndent()

        val activeContext = if (activeMeds.isNotEmpty()) {
            "Patient's saved medications: ${activeMeds.joinToString(", ")}.\n\n"
        } else ""

        val fullPrompt = "$systemPrompt\n\n${activeContext}User Question: $query"

        val payload = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            partsArray.put(JSONObject().put("text", fullPrompt))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
        val url = URL(urlString)

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-goog-api-key", apiKey)
            setRequestProperty("x-api-key", apiKey)
            doOutput = true
        }

        connection.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        if (connection.responseCode == 200) {
            val responseString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonRoot = JSONObject(responseString)
            val candidates = jsonRoot.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val reply = parts?.optJSONObject(0)?.optString("text")

            if (!reply.isNullOrBlank()) {
                return reply.trim()
            }
        }

        return ""
    }

    private fun getVerifiedClinicalKnowledge(query: String): String {
        val q = query.lowercase().trim()

        return when {
            q.contains("citrizine") || q.contains("cetirizine") || q.contains("cetrizine") || q.contains("okacet") || q.contains("alerid") || q.contains("zyrtec") -> """
                💊 Cetirizine Hydrochloride (Antihistamine / Antiallergic)
                
                • Generic Name: Cetirizine
                • Common Uses: Relieves symptoms of allergic rhinitis (sneezing, runny nose, watery eyes, itching) and chronic hives (urticaria).
                • Common Side Effects: Mild drowsiness, dry mouth, tiredness, or mild headache.
                • Key Precautions: May cause mild drowsiness; avoid driving or operating heavy machinery until you know how it affects you. Limit alcohol consumption.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("sinarest") || q.contains("solvin") || q.contains("wikoryl") -> """
                💊 Sinarest (Analgesic, Decongestant & Antihistamine)
                
                • Active Ingredients: Paracetamol + Phenylephrine + Chlorpheniramine.
                • Common Uses: Relief from common cold symptoms, fever, headache, nasal congestion, and runny nose.
                • Common Side Effects: Mild drowsiness, dizziness, dry mouth, or elevated blood pressure.
                • Key Precautions: Avoid combining with other paracetamol or decongestant medications. Take after meals to prevent stomach irritation.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("dolo") || q.contains("paracetamol") || q.contains("calpol") || q.contains("crocin") || q.contains("pacimol") -> """
                💊 Dolo 650 / Paracetamol (Analgesic & Antipyretic)
                
                • Generic Name: Paracetamol / Acetaminophen (650mg)
                • Common Uses: Reduces body temperature (fever) and relieves mild to moderate pain (headaches, body aches, joint pain).
                • Common Side Effects: Well tolerated when used as directed; rare allergic skin reactions.
                • Key Precautions: Do not exceed 4,000mg (4 grams) total per 24 hours to prevent liver toxicity. Maintain at least 4-6 hours interval between doses.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("azithral") || q.contains("azithromycin") || q.contains("azee") || q.contains("zithromax") -> """
                💊 Azithromycin / Azithral (Macrolide Antibiotic)
                
                • Generic Name: Azithromycin
                • Common Uses: Treats bacterial infections such as throat, sinus, lung (bronchitis/pneumonia), skin, and ear infections.
                • Common Side Effects: Nausea, mild diarrhea, abdominal cramps, or headache.
                • Key Precautions: Complete the full prescribed course even if symptoms improve. Take 1 hour before or 2 hours after meals unless stomach upset occurs.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("pantoprazole") || q.contains("pan 40") || q.contains("pan-40") || q.contains("pantocid") || q.contains("omez") || q.contains("esomeprazole") -> """
                💊 Pantoprazole / Pan 40 (Proton Pump Inhibitor / Antacid)
                
                • Generic Name: Pantoprazole Sodium
                • Common Uses: Treats gastroesophageal reflux disease (GERD), heartburn, stomach acidity, and gastric ulcers.
                • Common Side Effects: Mild headache, diarrhea, flatulence, or nausea.
                • Key Precautions: Best taken 30-60 minutes before the first meal of the day (morning on empty stomach). Swallow tablet whole; do not crush or chew.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("montair") || q.contains("montelukast") || q.contains("montek") -> """
                💊 Montelukast / Montair LC (Leukotriene Receptor Antagonist)
                
                • Generic Name: Montelukast Sodium (+ Levocetirizine if LC formulation)
                • Common Uses: Prevents asthma symptoms and treats allergic rhinitis (seasonal allergies, sneezing, nasal congestion).
                • Common Side Effects: Mild headache, abdominal pain, fever, or mild drowsiness.
                • Key Precautions: Usually taken once daily in the evening or at bedtime. Continue taking regularly as prescribed for long-term control.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("amoxicillin") || q.contains("mox") || q.contains("novamox") -> """
                💊 Amoxicillin (Penicillin Antibiotic)
                
                • Generic Name: Amoxicillin
                • Common Uses: Treats bacterial infections such as respiratory, sinus, ear, urinary, and dental infections.
                • Common Side Effects: Mild nausea, stomach upset, diarrhea, or skin rash.
                • Key Precautions: Complete the entire prescribed course. Inform your doctor if you have a penicillin allergy.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("metformin") || q.contains("glycomet") -> """
                💊 Metformin (Antidiabetic)
                
                • Generic Name: Metformin Hydrochloride
                • Common Uses: Management of type 2 diabetes to improve glycemic control.
                • Common Side Effects: Mild stomach upset, loose stools, or metallic taste when starting therapy.
                • Key Precautions: Always take with or immediately after meals to minimize stomach discomfort. Limit alcohol intake.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("atorvastatin") || q.contains("atorva") || q.contains("lipitor") -> """
                💊 Atorvastatin (Statin / Lipid-Lowering)
                
                • Generic Name: Atorvastatin Calcium
                • Common Uses: Lowers LDL ("bad") cholesterol and triglycerides, and reduces cardiovascular risk.
                • Common Side Effects: Mild muscle aches, joint pain, or mild digestive upset.
                • Key Precautions: Take once daily at bedtime or evening. Report unusual or severe muscle pain to your doctor.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("ibuprofen") || q.contains("combiflam") || q.contains("brufen") -> """
                💊 Ibuprofen / Combiflam (NSAID Pain Reliever)
                
                • Generic Name: Ibuprofen (+ Paracetamol if Combiflam)
                • Common Uses: Relieves inflammation, swelling, joint pain, toothache, and fever.
                • Common Side Effects: Stomach upset, heartburn, or nausea.
                • Key Precautions: Always take with food or milk to protect stomach lining. Avoid taking on an empty stomach.
                
                Disclaimer: This information is for educational purposes and is not a substitute for clinical advice from your prescribing doctor.
            """.trimIndent()

            q.contains("empty stomach") || q.contains("before food") || q.contains("after food") -> """
                💊 Meal Timing Information
                
                • Before Food (Empty Stomach): Antacids (like Pantoprazole/Omez) and thyroid medications are best taken 30-60 mins before breakfast.
                • After Food: Pain relievers (like Ibuprofen/Combiflam) and antibiotics should be taken with or after meals to prevent stomach irritation.
                
                Disclaimer: Always follow the specific instructions printed on your doctor's prescription.
            """.trimIndent()

            else -> """
                📋 Clinical Health Information
                
                Regarding "$query":
                • This assistant provides verified pharmacological guidance on side effects, meal instructions, and precautions.
                • Please specify the exact medicine name (e.g., Paracetamol, Amoxicillin, Cetirizine, Dolo, Metformin) or tap one of your active medicine buttons above.
                
                Disclaimer: This assistant provides general pharmacological information only and cannot diagnose conditions or alter prescribed doses.
            """.trimIndent()
        }
    }
}
