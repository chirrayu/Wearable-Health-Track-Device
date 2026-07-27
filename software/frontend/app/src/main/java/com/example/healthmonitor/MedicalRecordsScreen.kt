/*Medical Records screen — soldier picker on the left (same pattern as
  ConfigureSuitScreen), full editable medical profile on the right: blood
  group (read from Soldier), allergies, known conditions, emergency contact,
  free-text notes, and an injury/treatment log.*/
package com.example.healthmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MedicalRecordsScreen() {

    val soldiers = SoldierState.soldiers
    var selectedId by remember { mutableStateOf(soldiers.firstOrNull()?.id) }
    val selectedSoldier = soldiers.find { it.id == selectedId }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        // ── Soldier picker sidebar ──────────────────────────────────
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(cardDark)
                .border(width = 1.dp, color = borderDark)
                .padding(12.dp)
        ) {
            Text(
                text = "SELECT SOLDIER",
                color = textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(soldiers, key = { it.id }) { soldier ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (soldier.id == selectedId) accentBlue.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (soldier.id == selectedId) accentBlue.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedId = soldier.id }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor(soldier.status), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${soldier.rankTitle} ${soldier.name}",
                                color = if (soldier.id == selectedId) Color.White else Color(0xFFB8C6D9),
                                fontSize = 13.sp,
                                fontWeight = if (soldier.id == selectedId) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(soldier.serial, color = textMuted, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(statusRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(soldier.bloodGroup, color = statusRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Record panel ─────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (selectedSoldier == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No soldier selected", color = textMuted)
                }
            } else {
                MedicalRecordPanel(soldier = selectedSoldier)
            }
        }
    }
}

@Composable
fun MedicalRecordPanel(soldier: Soldier) {

    val record = MedicalRecordState.getRecord(soldier.id)

    var newAllergy by remember(soldier.id) { mutableStateOf("") }
    var newCondition by remember(soldier.id) { mutableStateOf("") }
    var newInjury by remember(soldier.id) { mutableStateOf("") }
    var newTreatment by remember(soldier.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentBlue.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, accentBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = accentBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "${soldier.rankTitle} ${soldier.name}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${soldier.serial} · ${soldier.squad} · ${soldier.role}",
                    color = textMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(statusRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, statusRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bloodtype, contentDescription = null, tint = statusRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(soldier.bloodGroup, color = statusRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Allergies ────────────────────────────────────────────
        RecordSection(title = "ALLERGIES", icon = Icons.Default.HealthAndSafety) {
            ChipEditor(
                items = record.allergies,
                inputValue = newAllergy,
                onInputChange = { newAllergy = it },
                onAdd = {
                    if (newAllergy.isNotBlank()) {
                        record.allergies.add(newAllergy.trim())
                        newAllergy = ""
                    }
                },
                onRemove = { record.allergies.remove(it) },
                placeholder = "Add allergy…",
                chipColor = statusRed
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Conditions ───────────────────────────────────────────
        RecordSection(title = "KNOWN CONDITIONS", icon = Icons.Default.Notes) {
            ChipEditor(
                items = record.conditions,
                inputValue = newCondition,
                onInputChange = { newCondition = it },
                onAdd = {
                    if (newCondition.isNotBlank()) {
                        record.conditions.add(newCondition.trim())
                        newCondition = ""
                    }
                },
                onRemove = { record.conditions.remove(it) },
                placeholder = "Add condition…",
                chipColor = statusYellow
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Emergency contact ───────────────────────────────────
        RecordSection(title = "EMERGENCY CONTACT", icon = Icons.Default.ContactPhone) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecordTextField(
                    value = record.emergencyContactName,
                    onValueChange = { record.emergencyContactName = it },
                    label = "Name",
                    modifier = Modifier.weight(1f)
                )
                RecordTextField(
                    value = record.emergencyContactRelation,
                    onValueChange = { record.emergencyContactRelation = it },
                    label = "Relation",
                    modifier = Modifier.weight(1f)
                )
                RecordTextField(
                    value = record.emergencyContactPhone,
                    onValueChange = { record.emergencyContactPhone = it },
                    label = "Phone",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Notes ────────────────────────────────────────────────
        RecordSection(title = "NOTES", icon = Icons.Default.Notes) {
            RecordTextField(
                value = record.notes,
                onValueChange = { record.notes = it },
                label = "Additional medical notes",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Injury / treatment log ──────────────────────────────
        RecordSection(title = "INJURY & TREATMENT LOG", icon = Icons.Default.History) {
            if (record.injuryLog.isEmpty()) {
                Text("No entries yet", color = textMuted, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    record.injuryLog.sortedByDescending { it.timestamp }.forEach { entry ->
                        InjuryLogRow(entry)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column {
                RecordTextField(
                    value = newInjury,
                    onValueChange = { newInjury = it },
                    label = "Injury / condition",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                RecordTextField(
                    value = newTreatment,
                    onValueChange = { newTreatment = it },
                    label = "Treatment given",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, accentBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(enabled = newInjury.isNotBlank()) {
                            record.injuryLog.add(
                                InjuryLogEntry(
                                    timestamp = "Today",
                                    description = newInjury.trim(),
                                    treatment = newTreatment.trim().ifBlank { "—" }
                                )
                            )
                            newInjury = ""
                            newTreatment = ""
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = accentBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LOG ENTRY", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun RecordSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardDark, RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun ChipEditor(
    items: List<String>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    placeholder: String,
    chipColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .background(chipColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, chipColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item, color = chipColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = chipColor,
                        modifier = Modifier.size(12.dp).clickable { onRemove(item) }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordTextField(
                value = inputValue,
                onValueChange = onInputChange,
                label = placeholder,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(borderDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onAdd() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = accentBlue, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RecordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        Text(label, color = textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgDark, RoundedCornerShape(8.dp))
                .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(accentBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun InjuryLogRow(entry: InjuryLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgDark, RoundedCornerShape(8.dp))
            .border(1.dp, borderDark, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Icon(Icons.Default.LocalHospital, contentDescription = null, tint = statusYellow, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(entry.timestamp, color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(entry.description, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Treatment: ${entry.treatment}", color = textMuted, fontSize = 12.sp)
        }
    }
}
