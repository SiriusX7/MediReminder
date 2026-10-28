package com.medassist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun UpdateStockDialog(
    medicine: Medicine,
    onSaveStock: (medicineId: Int, stock: Int, refillAt: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    var stockStr by remember(medicine.id) { mutableStateOf(medicine.stock.toString()) }
    var refillAtStr by remember(medicine.id) { mutableStateOf(medicine.refillAt.toString()) }
    var quantityStr by remember(medicine.id) { mutableStateOf(medicine.quantity.toString()) }
    var enableAlerts by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Update Stock: ${medicine.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enter current stock level and refill alert threshold. Inventory will decrease only when marked Taken.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Remaining Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = medicine.form.noun + "s",
                        onValueChange = {},
                        label = { Text("Unit") },
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Qty/Dose") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = refillAtStr,
                        onValueChange = { refillAtStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Alert Below") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = enableAlerts
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Refill Alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Switch(
                        checked = enableAlerts,
                        onCheckedChange = { enableAlerts = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = cs.onPrimary, checkedTrackColor = cs.primary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stockStr.toIntOrNull()?.coerceAtLeast(0) ?: medicine.stock
                    val r = if (enableAlerts) (refillAtStr.toIntOrNull()?.coerceAtLeast(0) ?: medicine.refillAt) else 0
                    onSaveStock(medicine.id, s, r)
                },
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
            ) {
                Text("Save Inventory", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
