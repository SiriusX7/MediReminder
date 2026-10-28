package com.medassist

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val prettyFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun dateToMillis(iso: String): Long = try {
    isoFormat.parse(iso)?.time ?: 0L
} catch (e: Exception) {
    0L
}

private fun millisToIso(millis: Long): String = isoFormat.format(Date(millis))

private fun prettyDate(iso: String): String = try {
    isoFormat.parse(iso)?.let { prettyFormat.format(it) } ?: iso
} catch (e: Exception) {
    iso
}

private fun formatTime12(time24: String): String {
    val parts = time24.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (hour >= 12) "PM" else "AM"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "%d:%02d %s".format(h12, minute, amPm)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicineFormScreen(
    draft: Medicine,
    isNew: Boolean,
    nextId: Int = 100,
    autoOpenScan: Boolean = false,
    onSave: (Medicine) -> Unit,
    onSaveMultiple: ((List<Medicine>) -> Unit)? = null,
    onDelete: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }

    var form by remember(draft.id) { mutableStateOf(draft) }
    var strengthStr by remember(draft.id) { mutableStateOf(draft.strength) }
    var mealInstructionChoice by remember(draft.id) { mutableStateOf(draft.mealInstruction) }
    var quantityStr by remember(draft.id) { mutableStateOf(draft.quantity.toString()) }
    var stockStr by remember(draft.id) { mutableStateOf(if (isNew && draft.name.isEmpty()) "" else draft.stock.toString()) }
    var refillAtStr by remember(draft.id) { mutableStateOf(draft.refillAt.toString()) }
    var nameError by remember { mutableStateOf(false) }
    var stockError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showScanOptionDialog by remember { mutableStateOf(false) }

    val initialTimeParts = remember(draft.id) { draft.time24.split(":") }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTimeParts.getOrNull(0)?.toIntOrNull() ?: 8,
        initialMinute = initialTimeParts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = false,
    )

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateToMillis(draft.startDate)
    )

    LaunchedEffect(autoOpenScan) {
        if (autoOpenScan) {
            delay(150)
            showScanOptionDialog = true
        }
    }
    var showScanWarningDialog by remember { mutableStateOf(false) }
    var showCandidateSelectionDialog by remember { mutableStateOf(false) }
    var scannedCandidates by remember { mutableStateOf<List<ScannedMedicineCandidate>>(emptyList()) }
    var selectedCandidateIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    fun selectCandidate(candidate: ScannedMedicineCandidate) {
        form = form.copy(
            name = candidate.name.ifEmpty { form.name },
            form = candidate.form,
            reason = candidate.instructions.ifEmpty { form.reason },
            quantity = candidate.quantity,
        )
        quantityStr = candidate.quantity.toString()
        nameError = false
        showCandidateSelectionDialog = false
    }

    fun saveSelectedCandidates() {
        val selectedCandidates = scannedCandidates.filterIndexed { index, _ -> index in selectedCandidateIndices }
        if (selectedCandidates.isEmpty()) return

        if (selectedCandidates.size == 1) {
            selectCandidate(selectedCandidates.first())
        } else {
            val medicineList = selectedCandidates.mapIndexed { index, candidate ->
                draft.copy(
                    id = nextId + index,
                    name = candidate.name,
                    form = candidate.form,
                    reason = candidate.instructions,
                    quantity = candidate.quantity,
                    stock = 30,
                    refillAt = 5,
                )
            }
            showCandidateSelectionDialog = false
            if (onSaveMultiple != null) {
                onSaveMultiple(medicineList)
            } else {
                selectCandidate(selectedCandidates.first())
            }
        }
    }

    fun processOcrUri(uri: Uri) {
        isScanning = true
        scope.launch {
            val parsedInfo = OcrTextRecognizer.recognizeTextFromUri(context, uri)
            isScanning = false
            if (parsedInfo != null && parsedInfo.isValidPrescription) {
                if (parsedInfo.candidates.size > 1) {
                    scannedCandidates = parsedInfo.candidates
                    selectedCandidateIndices = parsedInfo.candidates.indices.toSet()
                    showCandidateSelectionDialog = true
                } else if (parsedInfo.candidates.size == 1) {
                    selectCandidate(parsedInfo.candidates.first())
                } else if (parsedInfo.name.isNotEmpty()) {
                    form = form.copy(
                        name = parsedInfo.name,
                        form = parsedInfo.form,
                        reason = parsedInfo.instructions.ifEmpty { form.reason },
                        quantity = parsedInfo.quantity,
                    )
                    quantityStr = parsedInfo.quantity.toString()
                    nameError = false
                } else {
                    showScanWarningDialog = true
                }
            } else {
                showScanWarningDialog = true
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processOcrUri(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            processOcrUri(tempPhotoUri!!)
        }
    }

    fun launchCamera() {
        try {
            val tempFile = File.createTempFile("ocr_scan_", ".jpg", context.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempPhotoUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun onTakeCameraPhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun submitSave() {
        var hasError = false
        if (form.name.isBlank()) {
            nameError = true
            hasError = true
        }
        if (stockStr.isBlank()) {
            stockError = true
            hasError = true
        }

        if (!hasError) {
            val q = quantityStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val s = stockStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val r = refillAtStr.toIntOrNull()?.coerceAtLeast(0) ?: 2
            onSave(
                form.copy(
                    name = form.name.trim(),
                    strength = strengthStr.ifBlank { "500 mg" },
                    mealInstruction = mealInstructionChoice,
                    quantity = q,
                    stock = s,
                    refillAt = r
                )
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = cs.surface) {
        Scaffold(
            containerColor = cs.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(if (isNew) "Add medicine" else "Edit medicine") },
                        navigationIcon = {
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            if (!isNew) {
                                IconButton(onClick = { onDelete(form.id) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete medicine",
                                        tint = cs.error,
                                    )
                                }
                            }
                        },
                    )
                },
            ) { inner ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OutlinedCard(
                        onClick = { showScanOptionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Column {
                                    Text(
                                        "Scan Prescription / Label",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface,
                                    )
                                    Text(
                                        "Auto-fill details using ML Kit OCR",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = cs.primary
                                )
                            } else {
                                TextButton(
                                    onClick = { showScanOptionDialog = true }
                                ) {
                                    Text("Scan")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = {
                                form = form.copy(name = it)
                                if (it.isNotBlank()) nameError = false
                            },
                            label = { Text("Medicine name *") },
                            isError = nameError,
                            supportingText = { if (nameError) Text("Name is required") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                        )
                        OutlinedTextField(
                            value = strengthStr,
                            onValueChange = { strengthStr = it },
                            label = { Text("Strength") },
                            placeholder = { Text("500 mg") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f),
                        )
                    }

                    SectionLabel("Form")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MedForm.entries.forEach { mf ->
                            FilterChip(
                                selected = form.form == mf,
                                onClick = { form = form.copy(form = mf) },
                                label = { Text(mf.label) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = form.reason,
                        onValueChange = { form = form.copy(reason = it) },
                        label = { Text("Dosage Instructions") },
                        placeholder = { Text("After breakfast") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionLabel("Meal Instruction")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val mealOptions = listOf("Before food", "After food", "With food", "None")
                        mealOptions.forEach { option ->
                            FilterChip(
                                selected = mealInstructionChoice.equals(option, ignoreCase = true),
                                onClick = { mealInstructionChoice = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    SectionLabel("Frequency")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FrequencyType.entries.forEach { t ->
                            FilterChip(
                                selected = form.frequency.type == t,
                                onClick = {
                                    form = form.copy(frequency = form.frequency.copy(type = t))
                                },
                                label = { Text(t.short) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PickerField(
                            label = "Start date",
                            value = prettyDate(form.startDate),
                            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = cs.primary) },
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker = true },
                        )
                        PickerField(
                            label = "Time",
                            value = formatTime12(form.time24),
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = cs.primary) },
                            modifier = Modifier.weight(1f),
                            onClick = { showTimePicker = true },
                        )
                    }

                    SectionLabel("Inventory")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("Quantity per dose") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        OutlinedTextField(
                            value = stockStr,
                            onValueChange = {
                                stockStr = it
                                if (it.isNotBlank()) stockError = false
                            },
                            label = { Text("Amount left *") },
                            isError = stockError,
                            supportingText = { if (stockError) Text("Required") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        OutlinedTextField(
                            value = refillAtStr,
                            onValueChange = { refillAtStr = it },
                            label = { Text("Refill at") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Text(
                        if (form.stock <= form.refillAt) {
                            "Low stock — time to refill soon."
                        } else {
                            "You'll get a refill reminder when ${form.refillAt} left."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (form.stock <= form.refillAt) cs.error else cs.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { submitSave() },
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isNew) "Save Medicine" else "Save Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    form = form.copy(time24 = "%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            },
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { form = form.copy(startDate = millisToIso(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showScanOptionDialog) {
        AlertDialog(
            onDismissRequest = { showScanOptionDialog = false },
            title = {
                Text(
                    "Scan Prescription / Label",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Choose how you want to scan your medicine label:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedCard(
                        onClick = {
                            showScanOptionDialog = false
                            onTakeCameraPhoto()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Take Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Use camera to scan physical label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            showScanOptionDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Choose from Gallery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Select existing photo from device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showScanOptionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScanWarningDialog) {
        AlertDialog(
            onDismissRequest = { showScanWarningDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Scan Not Completed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "The scanned document does not appear to be a valid doctor's prescription or medicine label. Please scan the correct prescription.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { showScanWarningDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showCandidateSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showCandidateSelectionDialog = false },
            title = {
                Column {
                    Text(
                        "Select Medicines (${selectedCandidateIndices.size}/${scannedCandidates.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Check medicines to add simultaneously, or tap ✏️ to edit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedCandidateIndices = if (selectedCandidateIndices.size == scannedCandidates.size) {
                                    emptySet()
                                } else {
                                    scannedCandidates.indices.toSet()
                                }
                            }
                        ) {
                            Text(if (selectedCandidateIndices.size == scannedCandidates.size) "Deselect All" else "Select All")
                        }
                    }

                    scannedCandidates.forEachIndexed { index, candidate ->
                        val isChecked = index in selectedCandidateIndices
                        OutlinedCard(
                            onClick = {
                                selectedCandidateIndices = if (isChecked) {
                                    selectedCandidateIndices - index
                                } else {
                                    selectedCandidateIndices + index
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedCandidateIndices = if (checked) {
                                            selectedCandidateIndices + index
                                        } else {
                                            selectedCandidateIndices - index
                                        }
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (candidate.isDatabaseVerified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.medium
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        candidate.form.label.take(1),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (candidate.isDatabaseVerified) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            candidate.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (candidate.isDatabaseVerified) {
                                            Text(
                                                "✓ Verified (${candidate.apiSource})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        if (candidate.genericSalt.isNotEmpty()) "${candidate.genericSalt} • ${candidate.instructions}" else "${candidate.form.label} • ${candidate.instructions}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { selectCandidate(candidate) }
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "Edit medicine details",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            val searchQuery = scannedCandidates.firstOrNull()?.name ?: "prescription medicine"
                            uriHandler.openUri("https://www.google.com/search?q=" + Uri.encode(searchQuery))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Search Online for Medicine Info")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { saveSelectedCandidates() },
                    enabled = selectedCandidateIndices.isNotEmpty()
                ) {
                    Text("Add Selected (${selectedCandidateIndices.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCandidateSelectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.medium) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                Box(contentAlignment = Alignment.Center) { icon() }
            }
        }
    }
}
