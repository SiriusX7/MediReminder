package com.medi.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MedicineDto(
    val id: Int,
    val name: String,
    val formLabel: String,
    val reason: String,
    val frequencyType: String,
    val daysOfWeek: List<String>,
    val interval: Int,
    val unitLabel: String,
    val startDate: String,
    val time24: String,
    val quantity: Int,
    val stock: Int,
    val refillAt: Int,
    val colorArgb: Int,
    val paleArgb: Int,
    val takenDates: List<String> = emptyList(),
)

fun Medicine.toDto(): MedicineDto = MedicineDto(
    id = id,
    name = name,
    formLabel = form.name,
    reason = reason,
    frequencyType = frequency.type.name,
    daysOfWeek = frequency.daysOfWeek,
    interval = frequency.interval,
    unitLabel = frequency.unit.name,
    startDate = startDate,
    time24 = time24,
    quantity = quantity,
    stock = stock,
    refillAt = refillAt,
    colorArgb = color.toArgb(),
    paleArgb = pale.toArgb(),
    takenDates = takenDates.toList(),
)

fun MedicineDto.toDomain(): Medicine {
    val formEnum = MedForm.values().firstOrNull { it.name == formLabel } ?: MedForm.Pill
    val freqTypeEnum = FrequencyType.values().firstOrNull { it.name == frequencyType } ?: FrequencyType.Everyday
    val unitEnum = IntervalUnit.values().firstOrNull { it.name == unitLabel } ?: IntervalUnit.Days

    val datesSet = takenDates.toMutableSet()

    return Medicine(
        id = id,
        name = name,
        form = formEnum,
        reason = reason,
        frequency = Frequency(
            type = freqTypeEnum,
            daysOfWeek = daysOfWeek,
            interval = interval,
            unit = unitEnum,
        ),
        startDate = startDate,
        time24 = time24,
        quantity = quantity,
        stock = stock,
        refillAt = refillAt,
        color = Color(colorArgb),
        pale = Color(paleArgb),
        takenDates = datesSet,
    )
}

class DataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("medi_reminder_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadUserProfile(): UserProfile {
        val json = prefs.getString("user_profile", null) ?: return UserProfile()
        return try {
            gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
        } catch (e: Exception) {
            UserProfile()
        }
    }

    fun saveUserProfile(profile: UserProfile) {
        val json = gson.toJson(profile)
        prefs.edit().putString("user_profile", json).apply()
    }

    fun loadMedicines(): List<Medicine> {
        val json = prefs.getString("medicines_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MedicineDto>>() {}.type
            val dtos: List<MedicineDto> = gson.fromJson(json, type) ?: emptyList()
            dtos.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMedicines(medicines: List<Medicine>) {
        val dtos = medicines.map { it.toDto() }
        val json = gson.toJson(dtos)
        prefs.edit().putString("medicines_list", json).apply()
    }

    fun loadNextId(): Int {
        return prefs.getInt("next_id", 100)
    }

    fun saveNextId(id: Int) {
        prefs.edit().putInt("next_id", id).apply()
    }
}
