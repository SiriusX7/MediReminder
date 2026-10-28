package com.medassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat

@Composable
fun SettingsScreen(
    dataManager: DataManager? = null,
    onLanguageChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val dm = remember { dataManager ?: DataManager(context) }
    val cs = MaterialTheme.colorScheme

    var voiceRemindersEnabled by remember { mutableStateOf(dm.isVoiceRemindersEnabled()) }
    var soundEnabled by remember { mutableStateOf(dm.isSoundEnabled()) }
    var languageCode by remember { mutableStateOf(dm.getLanguageCode()) }

    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    fun sendTestNotification() {
        val channelId = "medassist_test_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MedAssist Dose Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dose reminder test notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Test Dose Reminder")
            .setContentText("This is a test dose alert from MedAssist.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(999, notification)
        Toast.makeText(context, "Test notification sent to status bar", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(cs.primaryContainer, shape = MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Category 1: Reminders & Notifications
        item {
            CategoryCard(title = stringResource(R.string.category_reminders)) {
                // Voice Reminders Row
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            BadgeIcon(icon = Icons.Default.VolumeUp, bgColor = Color(0xFFE0F2FE), iconColor = Color(0xFF0284C7))
                            Column {
                                Text(stringResource(R.string.setting_voice_reminders), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.setting_voice_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = voiceRemindersEnabled,
                            onCheckedChange = {
                                voiceRemindersEnabled = it
                                dm.setVoiceRemindersEnabled(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = cs.onPrimary, checkedTrackColor = cs.primary)
                        )
                    }

                    if (voiceRemindersEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                VoiceReminderManager.speakText(context, "This is a test voice reminder for MedAssist.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_test_voice))
                        }
                    }
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

                // Reminder Sound Chime Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        BadgeIcon(icon = Icons.Default.Notifications, bgColor = Color(0xFFDCFCE7), iconColor = Color(0xFF16A34A))
                        Column {
                            Text(stringResource(R.string.setting_sound), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.setting_sound_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            dm.setSoundEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = cs.onPrimary, checkedTrackColor = cs.primary)
                    )
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

                // Test Local Notifications Row
                SettingsActionRow(
                    icon = Icons.Default.NotificationsActive,
                    badgeBgColor = Color(0xFFFFEDD5),
                    badgeIconColor = Color(0xFFEA580C),
                    title = stringResource(R.string.setting_local_notifications),
                    subtitle = stringResource(R.string.setting_local_desc),
                    onClick = { sendTestNotification() }
                )
            }
        }

        // Category 2: Regional & Language
        item {
            CategoryCard(title = stringResource(R.string.category_regional)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BadgeIcon(icon = Icons.Default.Language, bgColor = Color(0xFFE0F2FE), iconColor = Color(0xFF0284C7))
                        Column {
                            Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.setting_language_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = languageCode == "system",
                            onClick = {
                                languageCode = "system"
                                dm.setLanguageCode("system")
                                LocaleHelper.applyLanguage(context, "system")
                                onLanguageChange?.invoke("system")
                            },
                            label = { Text("System") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = languageCode == "en",
                            onClick = {
                                languageCode = "en"
                                dm.setLanguageCode("en")
                                LocaleHelper.applyLanguage(context, "en")
                                onLanguageChange?.invoke("en")
                            },
                            label = { Text("English") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = languageCode == "hi",
                            onClick = {
                                languageCode = "hi"
                                dm.setLanguageCode("hi")
                                LocaleHelper.applyLanguage(context, "hi")
                                onLanguageChange?.invoke("hi")
                            },
                            label = { Text("हिंदी") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = languageCode == "te",
                            onClick = {
                                languageCode = "te"
                                dm.setLanguageCode("te")
                                LocaleHelper.applyLanguage(context, "te")
                                onLanguageChange?.invoke("te")
                            },
                            label = { Text("తెలుగు") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = languageCode == "pa",
                            onClick = {
                                languageCode = "pa"
                                dm.setLanguageCode("pa")
                                LocaleHelper.applyLanguage(context, "pa")
                                onLanguageChange?.invoke("pa")
                            },
                            label = { Text("ਪੰਜਾਬੀ") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Category 3: About & Support
        item {
            CategoryCard(title = stringResource(R.string.category_about)) {
                // Send Feedback Row
                SettingsActionRow(
                    icon = Icons.Default.Feedback,
                    badgeBgColor = Color(0xFFF3E8FF),
                    badgeIconColor = Color(0xFF9333EA),
                    title = stringResource(R.string.setting_feedback),
                    subtitle = stringResource(R.string.setting_feedback_desc),
                    onClick = { showFeedbackDialog = true }
                )

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

                // Clinical Safety Disclaimer Row
                SettingsActionRow(
                    icon = Icons.Outlined.Shield,
                    badgeBgColor = Color(0xFFFEF3C7),
                    badgeIconColor = Color(0xFFD97706),
                    title = stringResource(R.string.setting_disclaimer),
                    subtitle = stringResource(R.string.setting_disclaimer_desc),
                    onClick = { showDisclaimerDialog = true }
                )

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

                // App Version Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BadgeIcon(icon = Icons.Default.Info, bgColor = Color(0xFFF1F5F9), iconColor = Color(0xFF475569))
                        Column {
                            Text(stringResource(R.string.setting_version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.version_text), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Surface(color = cs.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text("1.0.0", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }

    // Disclaimer Dialog
    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color(0xFFD97706)) },
            title = { Text("Clinical Safety Disclaimer") },
            text = {
                Text(
                    "MedAssist is a medication reminder and educational tool. AI extraction and search may contain errors. Always verify medicine information against your original prescription before scheduling.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) {
                    Text("I Understand")
                }
            }
        )
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            icon = { Icon(Icons.Default.Feedback, contentDescription = null, tint = cs.primary) },
            title = { Text("Send Feedback") },
            text = {
                Text(
                    "Thank you for helping us improve MedAssist! You can share bug reports or feature suggestions with our development team.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFeedbackDialog = false
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@medassist.app?subject=MedAssist%20Feedback")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Feedback registered. Thank you!", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Open Email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cs.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, bgColor: Color, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    badgeBgColor: Color,
    badgeIconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            BadgeIcon(icon = icon, bgColor = badgeBgColor, iconColor = badgeIconColor)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
