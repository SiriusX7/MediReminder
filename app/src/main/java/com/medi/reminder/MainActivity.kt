package com.medi.reminder

import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medi.reminder.ui.theme.MediTheme
import com.medi.reminder.ui.theme.Success
import com.medi.reminder.ui.theme.SuccessContainer
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1_400)
                    showSplash = false
                }
                if (showSplash) {
                    MediSplashScreen()
                } else {
                    MediApp()
                }
            }
        }
    }
}

@Composable
private fun MediSplashScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "MediAssist",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Medicine reminders, made simple",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                )
            }
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        }
    }
}

private val TODAY: Int get() = getTodayDayOfMonth()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediApp() {
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var tab by remember { mutableStateOf("Today") }
    var nextId by remember { mutableStateOf(100) }
    val medicines = remember { mutableStateListOf(*initialMedicines.toTypedArray()) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var reminder by remember { mutableStateOf<Medicine?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val scheduledMedicines = medicines.filter { isMedicineScheduledOnDate(it, selectedCalendar) }
    val completed = scheduledMedicines.count { it.taken }

    fun toggle(id: Int) {
        val i = medicines.indexOfFirst { it.id == id }
        if (i >= 0) medicines[i] = medicines[i].copy(taken = !medicines[i].taken)
    }

    fun save(med: Medicine) {
        val i = medicines.indexOfFirst { it.id == med.id }
        if (i >= 0) medicines[i] = med else medicines.add(med)
    }

    fun delete(id: Int) {
        medicines.removeAll { it.id == id }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(tab) { tab = it } },
        floatingActionButton = {
            if (tab == "Today") {
                FloatingActionButton(
                    onClick = {
                        editing = emptyDraft(nextId)
                        nextId += 1
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add medicine reminder")
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            TopBar(
                onBell = {
                    reminder = medicines.firstOrNull { !it.taken } ?: medicines.firstOrNull()
                },
            )
            Spacer(Modifier.height(20.dp))

            if (tab == "Today") {
                DateSection(
                    selectedCalendar = selectedCalendar,
                    onSelectDate = { selectedCalendar = it },
                    onOpenCalendar = { showCalendar = true },
                )
                Spacer(Modifier.height(18.dp))
                ProgressCard(completed, scheduledMedicines.size)
                Spacer(Modifier.height(22.dp))
                ScheduleSection(
                    selectedCalendar = selectedCalendar,
                    medicines = scheduledMedicines,
                    onToggle = { toggle(it) },
                    onEdit = { editing = it },
                )
            } else {
                EmptyView(tab) { tab = "Today" }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showCalendar) {
        CalendarDialog(
            selectedCalendar = selectedCalendar,
            onSelectDate = {
                selectedCalendar = it
                showCalendar = false
            },
            onDismiss = { showCalendar = false },
        )
    }

    editing?.let { draft ->
        val isNew = medicines.none { it.id == draft.id }
        MedicineFormScreen(
            draft = draft,
            isNew = isNew,
            onSave = {
                save(it)
                editing = null
            },
            onDelete = {
                delete(it)
                editing = null
            },
            onCancel = { editing = null },
        )
    }

    reminder?.let { med ->
        ReminderScreen(
            medicine = med,
            onSnooze = { reminder = null },
            onTake = {
                toggle(med.id)
                reminder = null
            },
        )
    }
}

@Composable
private fun TopBar(onBell: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Good morning", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text("Hi, Maya", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = onBell,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Show reminder")
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("MB", color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun DateSection(
    selectedCalendar: Calendar,
    onSelectDate: (Calendar) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val monthDaysForCal = remember(selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH)) {
        getMonthDaysForCalendar(selectedCalendar)
    }

    val todayCal = Calendar.getInstance()
    val isToday = selectedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            selectedCalendar.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

    val titleText = if (isToday) "Today" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(selectedCalendar.time)
    val subtitleText = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(selectedCalendar.time)
    val selectedDay = selectedCalendar.get(Calendar.DAY_OF_MONTH)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(subtitleText, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        FilledTonalIconButton(
            onClick = onOpenCalendar,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = cs.surfaceContainerHighest,
                contentColor = cs.onSurface,
            ),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Open calendar")
        }
    }
    Spacer(Modifier.height(14.dp))

    val listState = rememberLazyListState()
    LaunchedEffect(selectedDay, selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.YEAR)) {
        val target = (selectedDay - 1).coerceIn(0, monthDaysForCal.lastIndex)
        listState.animateScrollToItem(target)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(monthDaysForCal, key = { it.date }) { day ->
            val active = selectedDay == day.date
            val isChipToday = isToday && day.date == todayCal.get(Calendar.DAY_OF_MONTH)
            Column(
                Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) cs.primary else cs.surfaceContainerLow)
                    .clickable {
                        val newCal = selectedCalendar.clone() as Calendar
                        newCal.set(Calendar.DAY_OF_MONTH, day.date)
                        onSelectDate(newCal)
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.day,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) cs.onPrimary.copy(alpha = 0.85f) else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${day.date}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) cs.onPrimary else cs.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isChipToday) {
                                if (active) cs.onPrimary else cs.primary
                            } else {
                                Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(completed: Int, total: Int) {
    val cs = MaterialTheme.colorScheme
    val ratio = if (total == 0) 0f else completed.toFloat() / total
    val animated by animateFloatAsState(ratio, label = "progress")
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = cs.inverseSurface,
            contentColor = cs.inverseOnSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.inverseOnSurface.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("You're on track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$completed of $total taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.inverseOnSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    color = SuccessContainer,
                    trackColor = cs.inverseOnSurface.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScheduleSection(
    selectedCalendar: Calendar,
    medicines: List<Medicine>,
    onToggle: (Int) -> Unit,
    onEdit: (Medicine) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val todayCal = Calendar.getInstance()
    val isToday = selectedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            selectedCalendar.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    val monthDayStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedCalendar.time)
    val sectionTitle = if (isToday) "Today's schedule" else "$monthDayStr schedule"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text("See all", style = MaterialTheme.typography.labelLarge, color = cs.primary)
    }
    Spacer(Modifier.height(14.dp))

    if (medicines.isEmpty()) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No medicines scheduled for this date",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap + below to add a new medicine reminder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    } else {
        medicines.forEachIndexed { index, medicine ->
            val (time, period) = formatTime(medicine.time24)
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(time, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(period, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Column(
                    Modifier.width(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (medicine.taken) medicine.color else cs.surface)
                            .border(2.dp, medicine.color, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (medicine.taken) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    if (index < medicines.size - 1) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(if (medicine.stock <= medicine.refillAt) 96.dp else 74.dp)
                                .background(cs.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Card(
                    onClick = { onEdit(medicine) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (medicine.taken) cs.surfaceContainer else cs.surfaceContainerLowest,
                    ),
                    border = if (medicine.taken) null else BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 14.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(medicine.pale),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Medication, contentDescription = null, tint = medicine.color, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(medicine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text(medicineDetail(medicine), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            TakeButton(medicine.taken) { onToggle(medicine.id) }
                        }
                        if (medicine.stock <= medicine.refillAt) {
                            Spacer(Modifier.height(10.dp))
                            AssistChip(
                                onClick = { onEdit(medicine) },
                                label = { Text("Refill soon · ${medicine.stock} left") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = cs.errorContainer,
                                    labelColor = cs.onErrorContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TakeButton(taken: Boolean, onClick: () -> Unit) {
    if (taken) {
        FilledTonalButton(
            onClick = onClick,
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = SuccessContainer,
                contentColor = Success,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Taken", modifier = Modifier.size(18.dp))
        }
    } else {
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp)) {
            Text("Take")
        }
    }
}

@Composable
private fun EmptyView(tab: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val icon = when (tab) {
        "Schedule" -> Icons.Outlined.DateRange
        "Medicine" -> Icons.Outlined.Medication
        else -> Icons.Outlined.Person
    }
    val copy = when (tab) {
        "Schedule" -> "Your weekly medication plan will appear here."
        "Medicine" -> "Manage your prescriptions and refill dates."
        else -> "Review your health profile and reminder preferences."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(tab, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(copy, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("Back to today") }
    }
}

@Composable
private fun BottomNav(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "Today" to Icons.Outlined.Home,
        "Schedule" to Icons.Outlined.CalendarMonth,
        "Medicine" to Icons.Outlined.Medication,
        "Profile" to Icons.Outlined.Person,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        items.forEach { (label, icon) ->
            NavigationBarItem(
                selected = current == label,
                onClick = { onSelect(label) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun CalendarDialog(
    selectedCalendar: Calendar,
    onSelectDate: (Calendar) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var displayedCalendar by remember(selectedCalendar) {
        mutableStateOf(selectedCalendar.clone() as Calendar)
    }

    val monthYearTitle = remember(displayedCalendar) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedCalendar.time)
    }

    val calFirst = (displayedCalendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val leading = calFirst.get(Calendar.DAY_OF_WEEK) - 1
    val maxDays = displayedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = List(leading) { 0 } + (1..maxDays).toList()

    val isSelectedMonth = displayedCalendar.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
            displayedCalendar.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)

    val todayCal = Calendar.getInstance()
    val isCurrentMonth = displayedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            displayedCalendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val newCal = displayedCalendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        displayedCalendar = newCal
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                }
                Text(
                    text = monthYearTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = {
                        val newCal = displayedCalendar.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        displayedCalendar = newCal
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                        Text(
                            d,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrElse(i) { 0 }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (d > 0) {
                                    val active = d == selectedCalendar.get(Calendar.DAY_OF_MONTH) && isSelectedMonth
                                    val isDayToday = d == todayCal.get(Calendar.DAY_OF_MONTH) && isCurrentMonth
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (active) cs.primary else Color.Transparent)
                                            .clickable {
                                                val chosenCal = displayedCalendar.clone() as Calendar
                                                chosenCal.set(Calendar.DAY_OF_MONTH, d)
                                                onSelectDate(chosenCal)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$d",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (active) cs.onPrimary else cs.onSurface,
                                            fontWeight = if (isDayToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
