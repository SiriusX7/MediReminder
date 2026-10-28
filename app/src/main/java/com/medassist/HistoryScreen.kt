package com.medassist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayAdherenceSummary(
    val dateString: String,
    val displayLabel: String,
    val dayInitial: String,
    val isToday: Boolean,
    val totalScheduled: Int,
    val takenCount: Int,
    val skippedCount: Int,
    val pendingCount: Int,
    val adherencePercent: Int?,
)

@Composable
fun HistoryScreen(
    medicines: List<Medicine>,
) {
    val cs = MaterialTheme.colorScheme

    val sevenDaySummaries = remember(medicines) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val initialFormat = SimpleDateFormat("EEEEE", Locale.getDefault())

        val dates = (0..6).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val dateStr = dateFormat.format(cal.time)
            val displayLabel = if (daysAgo == 0) "Today (${displayFormat.format(cal.time)})"
            else if (daysAgo == 1) "Yesterday (${displayFormat.format(cal.time)})"
            else displayFormat.format(cal.time)

            val initial = initialFormat.format(cal.time).uppercase()
            val isToday = daysAgo == 0

            // Filter scheduled medicines for this specific day
            val scheduledForDay = medicines.filter { isMedicineScheduledOnDate(it, cal) }
            val total = scheduledForDay.size
            val taken = scheduledForDay.count { it.takenDates.contains(dateStr) }
            val skipped = scheduledForDay.count { it.skippedDates.contains(dateStr) }
            val pending = (total - taken - skipped).coerceAtLeast(0)

            val percent = if (total > 0) ((taken.toFloat() / total.toFloat()) * 100).toInt() else null

            DayAdherenceSummary(
                dateString = dateStr,
                displayLabel = displayLabel,
                dayInitial = initial,
                isToday = isToday,
                totalScheduled = total,
                takenCount = taken,
                skippedCount = skipped,
                pendingCount = pending,
                adherencePercent = percent
            )
        }
        dates
    }

    val overallTaken = sevenDaySummaries.sumOf { it.takenCount }
    val overallTotal = sevenDaySummaries.sumOf { it.totalScheduled }
    val overallSkipped = sevenDaySummaries.sumOf { it.skippedCount }
    val overallPending = sevenDaySummaries.sumOf { it.pendingCount }
    val overallPercent = if (overallTotal > 0) ((overallTaken.toFloat() / overallTotal.toFloat()) * 100).toInt() else 100

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "7-Day Adherence & History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track medication adherence across the current day and past 6 calendar days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        // 2. 7-Day Adherence Score Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-Day Adherence Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Text(
                                text = "$overallTaken of $overallTotal doses taken ($overallSkipped skipped, $overallPending pending)",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$overallPercent%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEA580C)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { overallPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFFEA580C),
                        trackColor = cs.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 7-Day Circles Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        sevenDaySummaries.reversed().forEach { summary ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (summary.isToday) Color(0xFFEA580C)
                                            else cs.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (summary.isToday) "${summary.takenCount}/${summary.totalScheduled}" else "${summary.takenCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (summary.isToday) Color.White else cs.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (summary.isToday) "Today" else summary.dayInitial,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (summary.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (summary.isToday) cs.primary else cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Daily Adherence List Header
        item {
            Text(
                text = "Daily Adherence (Past 7 Days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
        }

        // 4. Daily Adherence Breakdown Cards
        items(sevenDaySummaries) { summary ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (summary.isToday) cs.surfaceVariant.copy(alpha = 0.5f) else cs.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = summary.displayLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            if (summary.isToday) {
                                Surface(
                                    color = cs.primary,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "CURRENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (summary.adherencePercent != null) {
                            Surface(
                                color = Color(0xFFFFEDD5),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${summary.adherencePercent}% Adherence",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEA580C),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = cs.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "No doses scheduled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Stat Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill("Total", summary.totalScheduled.toString(), cs.surfaceVariant, cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        StatPill("Taken", summary.takenCount.toString(), Color(0xFFDCFCE7), Color(0xFF16A34A), modifier = Modifier.weight(1f))
                        StatPill("Skipped", summary.skippedCount.toString(), Color(0xFFFFEDD5), Color(0xFFEA580C), modifier = Modifier.weight(1f))
                        StatPill("Pending", summary.pendingCount.toString(), Color(0xFFE0F2FE), Color(0xFF0284C7), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}
