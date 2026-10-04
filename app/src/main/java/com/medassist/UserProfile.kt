package com.medassist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class UserProfile(
    val firstName: String = "Maya",
    val lastName: String = "Baker",
    val age: Int = 28,
    val gender: String = "Female",
    val bloodGroup: String = "O+",
    val medicalConditions: String = "Vitamin D Deficiency",
    val emergencyContact: String = "+1 (555) 019-2834",
) {
    val name: String
        get() = "$firstName $lastName".trim()

    val initials: String
        get() {
            val f = firstName.trim().take(1).uppercase()
            val l = lastName.trim().take(1).uppercase()
            return when {
                f.isNotEmpty() && l.isNotEmpty() -> "$f$l"
                f.isNotEmpty() -> f
                l.isNotEmpty() -> l
                else -> "U"
            }
        }
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var firstName by remember(profile) { mutableStateOf(profile.firstName) }
    var lastName by remember(profile) { mutableStateOf(profile.lastName) }
    var ageStr by remember(profile) { mutableStateOf(profile.age.toString()) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var bloodGroup by remember(profile) { mutableStateOf(profile.bloodGroup) }
    var conditions by remember(profile) { mutableStateOf(profile.medicalConditions) }
    var contact by remember(profile) { mutableStateOf(profile.emergencyContact) }

    var isEditing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to home",
                    tint = cs.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Profile Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
        }
        // Header Card with Avatar
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.initials,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Text(
                    text = "${profile.age} years old · ${profile.gender}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // Details Section
        Text(
            text = "Personal Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )

        if (!isEditing) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileDetailRow(icon = Icons.Outlined.Person, label = "Full Name", value = profile.name)
                    ProfileDetailRow(icon = Icons.Outlined.Badge, label = "Age & Gender", value = "${profile.age} yrs · ${profile.gender}")
                    ProfileDetailRow(icon = Icons.Outlined.Bloodtype, label = "Blood Group", value = profile.bloodGroup)
                    ProfileDetailRow(icon = Icons.Outlined.WarningAmber, label = "Medical Conditions", value = profile.medicalConditions)
                    ProfileDetailRow(icon = Icons.Outlined.Call, label = "Emergency Contact", value = profile.emergencyContact)
                }
            }

            Button(
                onClick = { isEditing = true },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit Profile Details")
            }
        } else {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("First Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Last Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        ProfileDropdownField(
                            label = "Gender",
                            selectedValue = gender,
                            options = listOf("Male", "Female", "Other"),
                            onValueChange = { gender = it },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    ProfileDropdownField(
                        label = "Blood Group",
                        selectedValue = bloodGroup,
                        options = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
                        onValueChange = { bloodGroup = it },
                    )

                    OutlinedTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        label = { Text("Medical Conditions / Notes") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        label = { Text("Emergency Contact") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val parsedAge = ageStr.toIntOrNull() ?: profile.age
                            val updated = UserProfile(
                                firstName = firstName.trim().ifEmpty { profile.firstName },
                                lastName = lastName.trim().ifEmpty { profile.lastName },
                                age = parsedAge,
                                gender = gender,
                                bloodGroup = bloodGroup,
                                medicalConditions = conditions.trim().ifEmpty { "None" },
                                emergencyContact = contact.trim().ifEmpty { profile.emergencyContact },
                            )
                            onSaveProfile(updated)
                            isEditing = false
                        },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        }
    }
}
