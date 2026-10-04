package com.medi.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicineFormScreen(
    draft: Medicine,
    isNew: Boolean,
    onSave: (Medicine) -> Unit,
    onDelete: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var form by remember(draft.id) { mutableStateOf(draft) }
    var nameError by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun submitSave() {
        if (form.name.isBlank()) {
            nameError = true
        } else {
            onSave(form.copy(name = form.name.trim()))
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                            Button(
                                onClick = { submitSave() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.primary,
                                    contentColor = cs.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
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
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = {
                            form = form.copy(name = it)
                            if (it.isNotBlank()) nameError = false
                        },
                        label = { Text("Medicine name *") },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Please enter a medicine name", color = cs.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

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
                        label = { Text("Instructions") },
                        placeholder = { Text("After breakfast") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionLabel("Frequency")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val types = FrequencyType.entries
                        types.forEachIndexed { index, t ->
                            SegmentedButton(
                                selected = form.frequency.type == t,
                                onClick = { form = form.copy(frequency = form.frequency.copy(type = t)) },
                                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                            ) {
                                Text(t.short)
                            }
                        }
                    }

                    if (form.frequency.type == FrequencyType.Weekdays) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            weekdayNames.forEach { day ->
                                val selected = day in form.frequency.daysOfWeek
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) {
                                            form.frequency.daysOfWeek - day
                                        } else {
                                            form.frequency.daysOfWeek + day
                                        }
                                        form = form.copy(frequency = form.frequency.copy(daysOfWeek = next))
                                    },
                                    label = { Text(day) },
                                )
                            }
                        }
                    }

                    if (form.frequency.type == FrequencyType.Interval) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = form.frequency.interval.toString(),
                                onValueChange = { v ->
                                    val n = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                                    form = form.copy(frequency = form.frequency.copy(interval = n.coerceAtLeast(1)))
                                },
                                label = { Text("Every") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp),
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                                val units = IntervalUnit.entries
                                units.forEachIndexed { index, u ->
                                    SegmentedButton(
                                        selected = form.frequency.unit == u,
                                        onClick = { form = form.copy(frequency = form.frequency.copy(unit = u)) },
                                        shape = SegmentedButtonDefaults.itemShape(index, units.size),
                                    ) {
                                        Text(u.label)
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PickerField(
                            label = "Start date",
                            value = prettyDate(form.startDate),
                            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker = true },
                        )
                        PickerField(
                            label = "Time",
                            value = formatTimeFull(form.time24),
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.weight(1f),
                            onClick = { showTimePicker = true },
                        )
                    }

                    OutlinedTextField(
                        value = form.quantity.toString(),
                        onValueChange = { v ->
                            form = form.copy(quantity = (v.filter { it.isDigit() }.toIntOrNull() ?: 1).coerceAtLeast(1))
                        },
                        label = { Text("Quantity per dose") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = form.stock.toString(),
                            onValueChange = { v ->
                                form = form.copy(stock = v.filter { it.isDigit() }.toIntOrNull() ?: 0)
                            },
                            label = { Text("Amount left") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.refillAt.toString(),
                            onValueChange = { v ->
                                form = form.copy(refillAt = v.filter { it.isDigit() }.toIntOrNull() ?: 0)
                            },
                            label = { Text("Refill at") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
    }

    if (showTimePicker) {
        val parts = form.time24.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = false,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    form = form.copy(time24 = "%02d:%02d".format(timeState.hour, timeState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(form.startDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { form = form.copy(startDate = millisToIso(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
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
