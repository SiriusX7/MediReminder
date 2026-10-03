package com.medi.reminder

import androidx.compose.ui.graphics.Color
import com.medi.reminder.ui.theme.PillBlue
import com.medi.reminder.ui.theme.PillBluePale
import com.medi.reminder.ui.theme.PillOrange
import com.medi.reminder.ui.theme.PillOrangePale
import com.medi.reminder.ui.theme.PillPink
import com.medi.reminder.ui.theme.PillPinkPale
import com.medi.reminder.ui.theme.PillPurple
import com.medi.reminder.ui.theme.PillPurplePale
import com.medi.reminder.ui.theme.PillTeal
import com.medi.reminder.ui.theme.PillTealPale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class MedForm(val label: String, val noun: String) {
    Pill("Pill", "pill"),
    Injection("Injection", "shot"),
    Drops("Drops", "drop"),
    Inhaler("Inhaler", "puff"),
    Powder("Powder", "scoop"),
    Syrup("Syrup", "dose"),
}

enum class FrequencyType(val short: String) {
    Everyday("Daily"),
    EveryOther("Alt"),
    Weekdays("Days"),
    Interval("Every"),
}

enum class IntervalUnit(val label: String) {
    Days("days"),
    Weeks("weeks"),
    Months("months"),
}

data class Frequency(
    val type: FrequencyType = FrequencyType.Everyday,
    val daysOfWeek: List<String> = emptyList(),
    val interval: Int = 1,
    val unit: IntervalUnit = IntervalUnit.Days,
)

fun getIsoDateForCalendar(cal: Calendar): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(cal.time)
}

data class Medicine(
    val id: Int,
    val name: String,
    val form: MedForm,
    val reason: String,
    val frequency: Frequency,
    val startDate: String, // yyyy-MM-dd
    val time24: String, // HH:mm
    val quantity: Int,
    val stock: Int,
    val refillAt: Int,
    val color: Color,
    val pale: Color,
    val takenDates: Set<String> = emptySet(),
) {
    fun isTakenOn(dateIso: String): Boolean = takenDates.contains(dateIso)
    fun isTakenOn(cal: Calendar): Boolean = takenDates.contains(getIsoDateForCalendar(cal))
}

data class DayChip(val day: String, val date: Int)

data class Swatch(val color: Color, val pale: Color)

val palette = listOf(
    Swatch(PillPurple, PillPurplePale),
    Swatch(PillOrange, PillOrangePale),
    Swatch(PillTeal, PillTealPale),
    Swatch(PillPink, PillPinkPale),
    Swatch(PillBlue, PillBluePale),
)

val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun getTodayDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

fun getTodayIsoDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Calendar.getInstance().time)
}

fun getCurrentTime24(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Calendar.getInstance().time)
}

fun getMonthDaysForCalendar(cal: Calendar): List<DayChip> {
    val tempCal = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    return (1..maxDays).map { day ->
        val c = tempCal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, day)
        val dayName = dayFormat.format(c.time)
        DayChip(dayName, day)
    }
}

val monthDays: List<DayChip>
    get() = getMonthDaysForCalendar(Calendar.getInstance())

fun isMedicineScheduledOnDate(m: Medicine, targetCal: Calendar): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val startCal = Calendar.getInstance().apply {
        try {
            val parsed = sdf.parse(m.startDate) ?: return false
            time = parsed
        } catch (e: Exception) {
            return false
        }
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val target = (targetCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (target.before(startCal)) return false

    val diffMillis = target.timeInMillis - startCal.timeInMillis
    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

    return when (m.frequency.type) {
        FrequencyType.Everyday -> true
        FrequencyType.EveryOther -> diffDays % 2 == 0
        FrequencyType.Weekdays -> {
            val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)
            val targetDayShort = dayOfWeekFormat.format(target.time)
            m.frequency.daysOfWeek.any { it.equals(targetDayShort, ignoreCase = true) }
        }
        FrequencyType.Interval -> {
            val interval = m.frequency.interval.coerceAtLeast(1)
            when (m.frequency.unit) {
                IntervalUnit.Days -> diffDays % interval == 0
                IntervalUnit.Weeks -> diffDays % (interval * 7) == 0
                IntervalUnit.Months -> {
                    val startYear = startCal.get(Calendar.YEAR)
                    val startMonth = startCal.get(Calendar.MONTH)
                    val targetYear = target.get(Calendar.YEAR)
                    val targetMonth = target.get(Calendar.MONTH)
                    val monthDiff = (targetYear - startYear) * 12 + (targetMonth - startMonth)
                    monthDiff >= 0 && monthDiff % interval == 0 && target.get(Calendar.DAY_OF_MONTH) == startCal.get(Calendar.DAY_OF_MONTH)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers (mirror the React app's helpers).
// ---------------------------------------------------------------------------

/** "08:00" -> ("8:00", "AM"). */
fun formatTime(time24: String): Pair<String, String> {
    val parts = time24.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val period = if (h < 12) "AM" else "PM"
    val hour12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d".format(hour12, m) to period
}

/** "08:00" -> "8:00 AM". */
fun formatTimeFull(time24: String): String {
    val (t, p) = formatTime(time24)
    return "$t $p"
}

/** "1 pill · After breakfast" */
fun medicineDetail(m: Medicine): String {
    val noun = m.form.noun + if (m.quantity > 1) "s" else ""
    val reason = m.reason.trim()
    return if (reason.isEmpty()) "${m.quantity} $noun" else "${m.quantity} $noun · $reason"
}

fun frequencyLabel(f: Frequency): String = when (f.type) {
    FrequencyType.Everyday -> "Every day"
    FrequencyType.EveryOther -> "Every other day"
    FrequencyType.Weekdays ->
        if (f.daysOfWeek.isEmpty()) "Select days" else f.daysOfWeek.joinToString(", ")
    FrequencyType.Interval -> "Every ${f.interval} ${f.unit.label}"
}

fun emptyDraft(id: Int): Medicine {
    val swatch = palette[id % palette.size]
    return Medicine(
        id = id,
        name = "",
        form = MedForm.Pill,
        reason = "",
        frequency = Frequency(),
        startDate = getTodayIsoDate(),
        time24 = getCurrentTime24(),
        quantity = 1,
        stock = 30,
        refillAt = 5,
        color = swatch.color,
        pale = swatch.pale,
        takenDates = emptySet(),
    )
}

val initialMedicines: List<Medicine> get() = emptyList()
