package com.medassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.medassist.data.local.MedicationDao
import com.medassist.data.local.UserProfile
import com.medassist.data.local.UserProfileDao
import com.medassist.data.repository.AuthRepository
import com.medassist.ui.auth.AuthViewModel
import com.medassist.ui.auth.ForgotPasswordScreen
import com.medassist.ui.auth.LoginScreen
import com.medassist.ui.auth.SignUpScreen
import com.medassist.ui.theme.MedAssistTheme
import com.medassist.ui.theme.Success
import com.medassist.ui.theme.SuccessContainer
import com.medassist.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MediReminderApp
        val medicationDao = app.database.medicationDao()
        val userProfileDao = app.database.userProfileDao()
        val authRepository = AuthRepository()
        
        printAppFingerprints() // Retrieve SHA-1 and SHA-256 for Firebase
        askNotificationPermission()
        
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        AlarmScheduler.scheduleAll(applicationContext, currentUid)

        setContent {
            MedAssistTheme {
                val navController = rememberNavController()
                val authViewModel = remember { AuthViewModel(authRepository, userProfileDao) }
                val currentUser = FirebaseAuth.getInstance().currentUser
                val startDestination = if (currentUser == null) "login" else "main"

                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1_400)
                    showSplash = false
                }

                if (showSplash) {
                    MedAssistSplashScreen()
                } else {
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToSignUp = { navController.navigate("signup") },
                                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                                onLoginSuccess = {
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid != null) {
                                        triggerSync(uid)
                                    }
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("signup") {
                            SignUpScreen(
                                viewModel = authViewModel,
                                onNavigateToLogin = { navController.popBackStack() },
                                onSignUpSuccess = {
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid != null) {
                                        triggerSync(uid)
                                    }
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("forgot_password") {
                            ForgotPasswordScreen(
                                viewModel = authViewModel,
                                onBackToLogin = { navController.popBackStack() }
                            )
                        }
                        composable("main") {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            val workInfo by WorkManager.getInstance(context)
                                .getWorkInfosByTagFlow("sync_worker")
                                .collectAsState(initial = emptyList())
                            
                            val isSyncing = workInfo.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

                            MedAssistApp(
                                userId = uid,
                                medicationDao = medicationDao,
                                userProfileDao = userProfileDao,
                                isSyncing = isSyncing,
                                onSignOut = {
                                    scope.launch {
                                        val unsyncedCount = withContext(Dispatchers.IO) {
                                            medicationDao.getUnsyncedCount(uid)
                                        }
                                        if (unsyncedCount > 0) {
                                            // Handled inside MedAssistApp via a state if we want a dialog
                                            // But for now, let's assume MedAssistApp will call onSignOut 
                                            // and we just need to provide the logic.
                                            // Actually, let's move the unsynced check into MedAssistApp
                                        } else {
                                            authRepository.signOut()
                                            withContext(Dispatchers.IO) {
                                                app.database.clearAllTables()
                                            }
                                            navController.navigate("login") {
                                                popUpTo("main") { inclusive = true }
                                            }
                                        }
                                    }
                                },
                                onForceSignOut = {
                                    authRepository.signOut()
                                    scope.launch(Dispatchers.IO) {
                                        app.database.clearAllTables()
                                    }
                                    navController.navigate("login") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerSync(userId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("userId" to userId))
            .addTag("sync_worker")
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "sync_data_$userId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    private fun printAppFingerprints() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.forEach { signature ->
                val sha1 = getFingerprint(signature, "SHA-1")
                val sha256 = getFingerprint(signature, "SHA-256")
                Log.d("Fingerprints", "SHA-1: $sha1")
                Log.d("Fingerprints", "SHA-256: $sha256")
            }
        } catch (e: Exception) {
            Log.e("Fingerprints", "Error getting fingerprints", e)
        }
    }

    private fun getFingerprint(signature: Signature, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MedAssistSplashScreen() {
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
                    text = "MedAssist",
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
fun MedAssistApp(
    userId: String,
    medicationDao: MedicationDao,
    userProfileDao: UserProfileDao,
    isSyncing: Boolean = false,
    onSignOut: () -> Unit,
    onForceSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var userProfile by remember(userId) { mutableStateOf(UserProfile(uid = userId)) }
    LaunchedEffect(userId) {
        val profile = userProfileDao.getUserProfile(userId)
        if (profile != null) {
            userProfile = profile
        }
    }
    
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var tab by remember { mutableStateOf("Today") }

    var showLogoutWarning by remember { mutableStateOf(false) }

    BackHandler(enabled = tab != "Today") {
        tab = "Today"
    }

    val medicationsFlow = remember(userId) { medicationDao.getAllMedications(userId) }
    val medications by medicationsFlow.collectAsState(initial = emptyList())
    val medicines = medications.map { it.toDomain() }
    
    var nextId by remember { mutableStateOf(100) }
    LaunchedEffect(medicines) {
        if (medicines.isNotEmpty()) {
            nextId = (medicines.maxOf { it.id } + 1).coerceAtLeast(100)
        }
    }
    
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var reminder by remember { mutableStateOf<Medicine?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val scheduledMedicines = medicines.filter { isMedicineScheduledOnDate(it, selectedCalendar) }
    val completed = scheduledMedicines.count { it.isTakenOn(selectedCalendar) }

    fun toggle(id: Int, cal: Calendar) {
        val med = medicines.find { it.id == id }
        if (med != null) {
            val dateIso = getIsoDateForCalendar(cal)
            val newTakenDates = med.takenDates.toMutableSet()
            var newStock = med.stock
            if (newTakenDates.contains(dateIso)) {
                newTakenDates.remove(dateIso)
                newStock += med.quantity
            } else {
                newTakenDates.add(dateIso)
                newStock = maxOf(0, med.stock - med.quantity)
            }
            val updatedMed = med.copy(takenDates = newTakenDates, stock = newStock)
            scope.launch {
                medicationDao.update(updatedMed.toMedication())
                AlarmScheduler.scheduleNextAlarm(context, updatedMed.toMedication())
            }
        }
    }

    fun save(med: Medicine) {
        scope.launch {
            val medWithUser = if (med.userId == "local_user" && userId != "local_user") {
                med.copy(userId = userId)
            } else {
                med
            }
            medicationDao.insert(medWithUser.toMedication())
            AlarmScheduler.scheduleNextAlarm(context, medWithUser.toMedication())
        }
    }

    fun saveMultiple(list: List<Medicine>) {
        scope.launch {
            list.forEach { med ->
                val medWithUser = if (med.userId == "local_user" && userId != "local_user") {
                    med.copy(userId = userId)
                } else {
                    med
                }
                medicationDao.insert(medWithUser.toMedication())
                AlarmScheduler.scheduleNextAlarm(context, medWithUser.toMedication())
            }
        }
    }

    fun delete(id: Int) {
        val med = medicines.find { it.id == id }
        if (med != null) {
            scope.launch {
                medicationDao.delete(med.toMedication())
                AlarmScheduler.cancelAlarm(context, id)
            }
        }
    }

    fun updateProfile(newProfile: UserProfile) {
        userProfile = newProfile
        scope.launch {
            userProfileDao.insert(newProfile)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(tab) { tab = it } },
        floatingActionButton = {
            if (tab == "Today" || tab == "Medicine") {
                FloatingActionButton(
                    onClick = {
                        editing = emptyDraft(nextId, userId)
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
                userProfile = userProfile,
                onBell = {
                    reminder = scheduledMedicines.firstOrNull { !it.isTakenOn(selectedCalendar) } ?: scheduledMedicines.firstOrNull()
                },
                onAmbulanceClick = {
                    val number = userProfile.emergencyContact.trim().ifEmpty { "112" }
                    val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$number") }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                    }
                },
                onAvatarClick = { tab = "Profile" },
            )
            Spacer(Modifier.height(20.dp))

            if (tab == "Today") {
                if (isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        "Syncing your data...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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
                    onToggle = { toggle(it, selectedCalendar) }
                )
            } else if (tab == "Medicine") {
                MedicineListScreen(
                    medicines = medicines,
                    onEdit = { editing = it }
                )
            } else if (tab == "Profile") {
                ProfileScreen(
                    profile = userProfile,
                    onSaveProfile = { updateProfile(it) },
                    onBack = { tab = "Today" },
                    onSignOut = {
                        scope.launch {
                            val unsyncedCount = withContext(Dispatchers.IO) {
                                medicationDao.getUnsyncedCount(userId)
                            }
                            if (unsyncedCount > 0) {
                                showLogoutWarning = true
                            } else {
                                onSignOut()
                            }
                        }
                    }
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
            nextId = nextId,
            onSave = {
                save(it)
                editing = null
            },
            onSaveMultiple = { list ->
                saveMultiple(list)
                nextId += list.size
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
                toggle(med.id, Calendar.getInstance())
                reminder = null
            },
        )
    }

    if (showLogoutWarning) {
        AlertDialog(
            onDismissRequest = { showLogoutWarning = false },
            title = { Text("Unsynced Changes") },
            text = { Text("You have unsynced medications. If you sign out now, these changes may be lost. Do you want to sign out anyway?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutWarning = false
                        onForceSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getGreetingMessage(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
private fun TopBar(
    userProfile: UserProfile,
    onBell: () -> Unit,
    onAmbulanceClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val greeting = getGreetingMessage()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(greeting, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text("Hi, ${userProfile.firstName}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = onAmbulanceClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer,
                ),
            ) {
                Icon(Icons.Outlined.LocalHospital, contentDescription = "Call Ambulance")
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    userProfile.initials,
                    color = cs.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
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

    val titleText = if (isToday) "Today" else SimpleDateFormat("EEEE", Locale.getDefault()).format(selectedCalendar.time)
    val subtitleText = if (isToday) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(selectedCalendar.time)
    } else {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(selectedCalendar.time)
    }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isToday) {
                FilledTonalButton(
                    onClick = {
                        onSelectDate(Calendar.getInstance())
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer,
                    )
                ) {
                    Text("Today", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
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
            val isChipToday = day.date == todayCal.get(Calendar.DAY_OF_MONTH) && 
                              selectedCalendar.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                              selectedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                              
            val backgroundColor = when {
                active -> cs.primary
                isChipToday -> cs.secondaryContainer
                else -> cs.surfaceContainerLow
            }

            val textColor = when {
                active -> cs.onPrimary
                isChipToday -> cs.onSecondaryContainer
                else -> cs.onSurface
            }
            
            val labelColor = when {
                active -> cs.onPrimary.copy(alpha = 0.85f)
                isChipToday -> cs.onSecondaryContainer.copy(alpha = 0.85f)
                else -> cs.onSurfaceVariant
            }

            Column(
                Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(backgroundColor)
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
                    color = labelColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${day.date}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
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
) {
    val cs = MaterialTheme.colorScheme
    val todayCal = Calendar.getInstance()
    val isToday = selectedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            selectedCalendar.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    val monthDayStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedCalendar.time)
    val sectionTitle = if (isToday) "Today's schedule" else "$monthDayStr schedule"

    val sortedMedicines = remember(medicines) {
        medicines.sortedBy { it.time24 }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    }
    Spacer(Modifier.height(14.dp))

    if (sortedMedicines.isEmpty()) {
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
        sortedMedicines.forEachIndexed { index, medicine ->
            val (time, period) = formatTime(medicine.time24)
            val isTakenToday = medicine.isTakenOn(selectedCalendar)
            val isSkippedToday = medicine.isSkippedOn(selectedCalendar)
            val isPast = selectedCalendar.get(Calendar.YEAR) < todayCal.get(Calendar.YEAR) ||
                    (selectedCalendar.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                     selectedCalendar.get(Calendar.DAY_OF_YEAR) < todayCal.get(Calendar.DAY_OF_YEAR))

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
                    val timelineColor = when {
                        isTakenToday -> medicine.color
                        isSkippedToday -> cs.errorContainer
                        isPast -> cs.errorContainer
                        else -> cs.surface
                    }
                    val timelineBorderColor = when {
                        isTakenToday -> medicine.color
                        isSkippedToday -> cs.error
                        isPast -> cs.error
                        else -> medicine.color
                    }
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(timelineColor)
                            .border(2.dp, timelineBorderColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isTakenToday) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        } else if (isSkippedToday || isPast) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(cs.error),
                            )
                        }
                    }
                    if (index < medicines.size - 1) {
                        val cardHeight = if (isToday) {
                            if (medicine.stock <= medicine.refillAt) 96.dp else 108.dp
                        } else {
                            74.dp
                        }
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(cardHeight)
                                .background(cs.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTakenToday) cs.surfaceContainer else cs.surfaceContainerLowest,
                    ),
                    border = if (isTakenToday) null else BorderStroke(1.dp, cs.outlineVariant),
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
                            TakeButton(
                                taken = isTakenToday,
                                skipped = isSkippedToday,
                                enabled = isToday,
                                isPast = isPast
                            ) { onToggle(medicine.id) }
                        }
                        if (isToday) {
                            if (medicine.stock <= medicine.refillAt) {
                                Spacer(Modifier.height(10.dp))
                                AssistChip(
                                    onClick = { },
                                    label = { Text("Refill soon · ${medicine.stock} left") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = cs.errorContainer,
                                        labelColor = cs.onErrorContainer,
                                    ),
                                )
                            } else {
                                val initialStock = maxOf(medicine.stock + medicine.takenDates.size * medicine.quantity, medicine.quantity) 
                                val ratio = (medicine.stock.toFloat() / initialStock.toFloat()).coerceIn(0f, 1f)
                                val animatedRatio by animateFloatAsState(ratio, label = "stock_progress")
                                val progressColor = if (ratio <= 0.15f) cs.error else cs.primary
                                
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LinearProgressIndicator(
                                        progress = { animatedRatio },
                                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                                        color = progressColor,
                                        trackColor = progressColor.copy(alpha = 0.2f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("${medicine.stock} left", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TakeButton(taken: Boolean, skipped: Boolean, enabled: Boolean, isPast: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (taken) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SuccessContainer,
                contentColor = Success,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Taken", modifier = Modifier.size(18.dp))
        }
    } else if (skipped) {
        FilledTonalButton(
            onClick = {},
            enabled = false,
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = cs.errorContainer,
                disabledContentColor = cs.onErrorContainer,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text("Skipped", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    } else if (isPast) {
        FilledTonalButton(
            onClick = {},
            enabled = false,
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = cs.errorContainer,
                disabledContentColor = cs.onErrorContainer,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text("Missed", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 18.dp)) {
            Text("Take")
        }
    }
}

@Composable
private fun EmptyView(tab: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val icon = when (tab) {
        "Medicine" -> Icons.Outlined.Medication
        else -> Icons.Outlined.Person
    }
    val copy = when (tab) {
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
