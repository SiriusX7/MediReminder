package com.medi.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MedicineListScreen(medicines: List<Medicine>, onEdit: (Medicine) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Text("My Medicines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(16.dp))
        
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
                        "No medicines added yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Go to the 'Today' tab to add a new medicine reminder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        } else {
            medicines.forEach { medicine ->
                Card(
                    onClick = { onEdit(medicine) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(medicine.pale),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Medication, contentDescription = null, tint = medicine.color)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(medicine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                            Text(frequencyLabel(medicine.frequency), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${medicine.stock} left (Refill at ${medicine.refillAt})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (medicine.stock <= medicine.refillAt) cs.error else cs.primary
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Edit", tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}