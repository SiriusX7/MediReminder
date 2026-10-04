package com.medassist

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medassist.ui.theme.MedAssistTheme
import java.util.Calendar

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake up screen and show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val medicineId = intent.getIntExtra("MEDICINE_ID", -1)
        val dataManager = DataManager(this)
        val medicine = dataManager.loadMedicines().find { it.id == medicineId }

        if (medicine == null) {
            finish()
            return
        }

        playAlarmSound()

        setContent {
            MedAssistTheme {
                AlarmScreen(
                    medicine = medicine,
                    onTaken = {
                        markAsTaken(dataManager, medicine)
                        stopAlarmSound()
                        cancelNotification(medicineId)
                        finish()
                    },
                    onSnooze = { minutes ->
                        AlarmScheduler.snoozeAlarm(this, medicineId, minutes)
                        stopAlarmSound()
                        cancelNotification(medicineId)
                        finish()
                    }
                )
            }
        }
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }

    private fun cancelNotification(id: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)
    }

    private fun markAsTaken(dataManager: DataManager, medicine: Medicine) {
        val medicines = dataManager.loadMedicines().toMutableList()
        val index = medicines.indexOfFirst { it.id == medicine.id }
        if (index >= 0) {
            val med = medicines[index]
            val todayIso = getIsoDateForCalendar(Calendar.getInstance())
            val newTakenDates = med.takenDates.toMutableSet()
            newTakenDates.add(todayIso)
            
            // Adjust stock
            val newStock = (med.stock - med.quantity).coerceAtLeast(0)
            
            medicines[index] = med.copy(takenDates = newTakenDates, stock = newStock)
            dataManager.saveMedicines(medicines)
            
            // Schedule the NEXT alarm
            AlarmScheduler.scheduleNextAlarm(this, medicines[index])
        }
    }
}

@Composable
fun AlarmScreen(
    medicine: Medicine,
    onTaken: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var showSnoozeOptions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cs.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(medicine.pale),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = medicine.color,
                    modifier = Modifier.size(64.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Time for your medicine",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = medicine.name,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = medicineDetail(medicine),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                )
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Take Now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { showSnoozeOptions = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Filled.Snooze, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Snooze", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showSnoozeOptions) {
        AlertDialog(
            onDismissRequest = { showSnoozeOptions = false },
            title = { Text("Snooze for...") },
            text = {
                Column {
                    val options = listOf(5, 10, 15, 30, 60)
                    options.forEach { mins ->
                        val label = if (mins >= 60) "${mins / 60} hour" else "$mins minutes"
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSnoozeOptions = false
                                    onSnooze(mins)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnoozeOptions = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
