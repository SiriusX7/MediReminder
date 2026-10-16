package com.medassist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medassist.data.local.IntakeLog
import com.medassist.data.local.IntakeLogDao
import com.medassist.data.local.Medication
import com.medassist.data.local.MedicationDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MedicationViewModel(
    private val userId: String,
    private val medicationDao: MedicationDao,
    private val intakeLogDao: IntakeLogDao
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate

    val timelineState: StateFlow<List<MedicationWithLogs>> = combine(
        medicationDao.getAllMedications(userId),
        intakeLogDao.getAllLogs(userId),
        _selectedDate
    ) { medications, logs, date ->
        // In a real app, you'd filter logs by the selected date here.
        // For simplicity, we're showing all medications and their logs.
        medications.map { medication ->
            MedicationWithLogs(
                medication = medication,
                logs = logs.filter { it.medicationId == medication.id }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun takeMedication(medicationId: Int) {
        viewModelScope.launch {
            val medication = medicationDao.getMedicationById(medicationId, userId)
            if (medication != null && medication.stock > 0) {
                // Insert log
                intakeLogDao.insert(
                    IntakeLog(
                        userId = userId,
                        medicationId = medicationId,
                        timestamp = System.currentTimeMillis(),
                        status = "TAKEN"
                    )
                )
                // Update stock (assuming dosage is parsed or simplified here)
                medicationDao.update(medication.copy(stock = medication.stock - 1))
            }
        }
    }

    fun skipMedication(medicationId: Int) {
        viewModelScope.launch {
            intakeLogDao.insert(
                IntakeLog(
                    userId = userId,
                    medicationId = medicationId,
                    timestamp = System.currentTimeMillis(),
                    status = "SKIPPED"
                )
            )
        }
    }

    fun saveManualMedication(
        name: String,
        reason: String,
        frequencyType: String,
        time24: String,
        stock: Int
    ) {
        viewModelScope.launch {
            val medication = Medication(
                userId = userId,
                name = name,
                reason = reason,
                frequencyType = frequencyType,
                time24 = time24,
                stock = stock,
                isOcrScanned = false
            )
            medicationDao.insert(medication)
        }
    }

    fun setSelectedDate(calendar: Calendar) {
        _selectedDate.value = calendar
    }
}

data class MedicationWithLogs(
    val medication: Medication,
    val logs: List<IntakeLog>
)

class MedicationViewModelFactory(
    private val userId: String,
    private val medicationDao: MedicationDao,
    private val intakeLogDao: IntakeLogDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationViewModel(userId, medicationDao, intakeLogDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
