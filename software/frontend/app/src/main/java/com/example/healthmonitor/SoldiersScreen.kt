package com.example.healthmonitor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage

// ── Colors ────────────────────────────────────────────────────────
// Not private: reused across screens (e.g. ConfigureSuitScreen) to keep a
// single consistent tactical color palette for the whole app.
val bgDark      = Color(0xFF07111F)
val cardDark    = Color(0xFF081B33)
val borderDark  = Color(0xFF1A3A5C)
val textMuted   = Color(0xFF6B7F99)
val accentBlue  = Color(0xFF00C2FF)
val statusGreen = Color(0xFF00E676)
val statusYellow= Color(0xFFFFD600)
val statusRed   = Color(0xFFFF1744)
val statusGray  = Color(0xFF757575)

fun statusColor(status: String): Color = when (status) {
    "stable"   -> statusGreen
    "serious"  -> statusYellow
    "critical" -> statusRed
    else       -> statusGray
}

fun hrColor(hr: Int?): Color = when (hrZone(hr)) {
    "green"  -> statusGreen
    "yellow" -> statusYellow
    "red"    -> statusRed
    else     -> textMuted
}


// ── Main Soldiers Screen ──────────────────────────────────────────
@Composable
fun SoldiersScreen() {

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSoldier by remember { mutableStateOf<Soldier?>(null) }
    var deletingSoldier by remember { mutableStateOf<Soldier?>(null) }

    val selectedSquad by SquadState.selectedSquad

    val visibleSoldiers = SoldierState.soldiers
        .filter { selectedSquad == "All Squads" || it.squad == selectedSquad }
        .filter { selectedFilter == "All" || it.status == selectedFilter.lowercase() }
        .filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.serial.contains(searchQuery, ignoreCase = true)
        }
        .sortedBy { it.rankOrder }

    if (showAddDialog) {
        SoldierEditDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { soldier ->
                SoldierState.addSoldier(soldier)
                showAddDialog = false
            }
        )
    }

    editingSoldier?.let { soldier ->
        SoldierEditDialog(
            existing = soldier,
            onDismiss = { editingSoldier = null },
            onSave = { updated ->
                SoldierState.updateSoldier(updated)
                editingSoldier = null
            }
        )
    }

    deletingSoldier?.let { soldier ->
        AlertDialog(
            onDismissRequest = { deletingSoldier = null },
            containerColor = cardDark,
            title = { Text("Remove Soldier", color = Color.White) },
            text = {
                Text(
                    "Remove ${soldier.name} (${soldier.serial}) from the roster?",
                    color = textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SoldierState.removeSoldier(soldier.id)
                    deletingSoldier = null
                }) {
                    Text("Remove", color = statusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSoldier = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {

        SquadSidebar()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, accentBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = accentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ADD SOLDIER", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            FilterChips(
                selected = selectedFilter,
                onSelect = { selectedFilter = it }
            )

            Spacer(Modifier.height(12.dp))

            SoldierTable(
                soldiers = visibleSoldiers,
                onEdit = { editingSoldier = it },
                onDelete = { deletingSoldier = it }
            )
        }
    }
}


// ── Squad Sidebar ─────────────────────────────────────────────────
@Composable
fun SquadSidebar() {

    var showAddSquad by remember { mutableStateOf(false) }
    var newSquadName by remember { mutableStateOf("") }
    val selectedSquad by SquadState.selectedSquad

    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(cardDark)
            .border(width = 1.dp, color = borderDark)
            .padding(12.dp)
    ) {
        Text(
            text = "SQUADS",
            color = textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(12.dp))

        SquadRow("All Squads", selectedSquad == "All Squads") {
            SquadState.selectedSquad.value = "All Squads"
        }

        Spacer(Modifier.height(4.dp))

        SquadState.squads.forEach { squad ->
            SquadRow(squad, selectedSquad == squad) {
                SquadState.selectedSquad.value = squad
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))

        if (showAddSquad) {
            OutlinedTextField(
                value = newSquadName,
                onValueChange = { newSquadName = it },
                placeholder = { Text("Squad name", color = textMuted, fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = borderDark
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (newSquadName.isNotBlank()) {
                                SquadState.squads.add(newSquadName.trim())
                                newSquadName = ""
                                showAddSquad = false
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(borderDark, RoundedCornerShape(6.dp))
                        .clickable { showAddSquad = false; newSquadName = "" }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = textMuted, fontSize = 11.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderDark, RoundedCornerShape(6.dp))
                    .clickable { showAddSquad = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add squad", tint = textMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Squad", color = textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SquadRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) accentBlue.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) accentBlue.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = name,
            color = if (selected) accentBlue else textMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}


// ── Search Bar ─────────────────────────────────────────────────────
@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .background(cardDark, RoundedCornerShape(8.dp))
            .border(1.dp, borderDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search soldiers...", color = textMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(accentBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


// ── Filter Chips ───────────────────────────────────────────────────
@Composable
fun FilterChips(selected: String, onSelect: (String) -> Unit) {
    val filters = listOf("All", "Stable", "Serious", "Critical", "Offline")

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = selected == filter
            val color = when (filter) {
                "Stable"   -> statusGreen
                "Serious"  -> statusYellow
                "Critical" -> statusRed
                "Offline"  -> statusGray
                else       -> accentBlue
            }
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) color.copy(alpha = 0.15f) else cardDark,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) color else borderDark,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) color else textMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}


// ── Soldier Table ──────────────────────────────────────────────────
@Composable
fun SoldierTable(
    soldiers: List<Soldier>,
    onEdit: (Soldier) -> Unit,
    onDelete: (Soldier) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cardDark, RoundedCornerShape(10.dp))
            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderText("#", Modifier.width(28.dp))
            TableHeaderText("SOLDIER", Modifier.weight(2.2f))
            TableHeaderText("SQUAD", Modifier.weight(1f))
            TableHeaderText("ROLE", Modifier.weight(1.3f))
            TableHeaderText("HR", Modifier.weight(0.9f))
            TableHeaderText("SPO2", Modifier.weight(0.9f))
            TableHeaderText("TEMP", Modifier.weight(0.9f))
            TableHeaderText("BATTERY/RISK", Modifier.weight(1.6f))
            TableHeaderText("STATUS", Modifier.weight(1.1f))
            TableHeaderText("BLOOD", Modifier.weight(0.7f))   // ← ADD THIS
            Spacer(Modifier.width(60.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableHeaderText("#", Modifier.width(28.dp))
            TableHeaderText("SOLDIER", Modifier.weight(2.2f))
            TableHeaderText("SQUAD", Modifier.weight(1f))
            TableHeaderText("ROLE", Modifier.weight(1.3f))
            TableHeaderText("HR", Modifier.weight(0.9f))
            TableHeaderText("SPO2", Modifier.weight(0.9f))
            TableHeaderText("TEMP", Modifier.weight(0.9f))
            TableHeaderText("BATTERY/RISK", Modifier.weight(1.6f))
            TableHeaderText("STATUS", Modifier.weight(1.1f))
            Spacer(Modifier.width(60.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderDark))

        if (soldiers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No soldiers match your filters", color = textMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(soldiers.size) { index ->
                    val soldier = soldiers[index]
                    SoldierRow(
                        index = index + 1,
                        soldier = soldier,
                        onEdit = { onEdit(soldier) },
                        onDelete = { onDelete(soldier) }
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderDark.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

@Composable
fun TableHeaderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
    )
}


@Composable
fun SoldierRow(index: Int, soldier: Soldier, onEdit: () -> Unit, onDelete: () -> Unit) {

    var showActions by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showActions = !showActions }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(text = "$index", color = textMuted, fontSize = 13.sp, modifier = Modifier.width(28.dp))

        Row(
            modifier = Modifier.weight(2.2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoldierAvatar(soldier.photoUri, soldier.name)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "${soldier.rankTitle} ${soldier.name}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = soldier.serial, color = textMuted, fontSize = 11.sp)
            }
        }

        Text(text = soldier.squad, color = textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(text = soldier.role, color = textMuted, fontSize = 13.sp, modifier = Modifier.weight(1.3f))

        Row(
            modifier = Modifier.weight(0.9f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (soldier.hr != null) {
                PulseIcon(color = hrColor(soldier.hr))
                Spacer(Modifier.width(4.dp))
                Text(text = "${soldier.hr}", color = hrColor(soldier.hr), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(text = "—", color = textMuted, fontSize = 13.sp)
            }
        }

        Text(
            text = soldier.spo2?.let { "$it%" } ?: "—%",
            color = if (soldier.spo2 != null) accentBlue else textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.9f)
        )

        Text(
            text = soldier.temp?.let { "$it" } ?: "—",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(0.9f)
        )

        Box(modifier = Modifier.weight(1.6f)) {
            BatteryRiskBar(battery = soldier.battery, riskColor = statusColor(soldier.status))
        }

        Box(modifier = Modifier.weight(1.1f)) {
            StatusPill(soldier.status)
        }
        Box(
            modifier = Modifier.weight(0.7f),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFFF1744).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFFFF1744).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = soldier.bloodGroup,
                    color = Color(0xFFFF1744),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showActions) {
            Row {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = accentBlue,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onEdit() }
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = statusRed,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDelete() }
                )
            }
        } else {
            Spacer(Modifier.width(60.dp))
        }
    }
}


// ── Avatar (photo or initials) ────────────────────────────────────
@Composable
fun SoldierAvatar(photoUri: String?, name: String) {
    Box(
        modifier = Modifier
            .size(48.dp)                    // ← change to make big
            .clip(CircleShape)
            .background(cardDark)
            .border(1.dp, borderDark, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = Uri.parse(photoUri),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                color = textMuted,
                fontSize = 15.sp,            // ← slightly bigger to match
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ── Animated pulse icon ───────────────────────────────────────────
@Composable
fun PulseIcon(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleValue by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = "Heart rate",
        tint = color,
        modifier = Modifier
            .size(14.dp)
            .scale(scaleValue)
    )
}


// ── Battery / risk bar ─────────────────────────────────────────────
@Composable
fun BatteryRiskBar(battery: Int, riskColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(6.dp)
                .background(borderDark, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(battery / 100f)
                    .background(
                        if (battery < 20) statusRed else if (battery < 50) statusYellow else statusGreen,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "$battery%", color = textMuted, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${100 - battery}%",
            color = riskColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// ── Status pill ────────────────────────────────────────────────────
@Composable
fun StatusPill(status: String) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// ── Add / Edit Soldier Dialog ──────────────────────────────────────
@Composable
fun SoldierEditDialog(
    existing: Soldier?,
    onDismiss: () -> Unit,
    onSave: (Soldier) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var rankTitle by remember { mutableStateOf(existing?.rankTitle ?: "Pvt.") }
    var rankOrder by remember { mutableStateOf(existing?.rankOrder?.toString() ?: "4") }
    var serial by remember { mutableStateOf(existing?.serial ?: "S-00${SoldierState.soldiers.size + 1}") }
    var squad by remember { mutableStateOf(existing?.squad ?: SquadState.squads.firstOrNull() ?: "Alpha") }
    var role by remember { mutableStateOf(existing?.role ?: "Rifleman") }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }
    var bloodGroup by remember { mutableStateOf(existing?.bloodGroup ?: "O+") }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { photoUri = it.toString() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardDark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existing == null) "ADD SOLDIER" else "EDIT SOLDIER",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = textMuted,
                    modifier = Modifier.size(18.dp).clickable { onDismiss() }
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(bgDark)
                        .border(1.dp, borderDark, CircleShape)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = Uri.parse(photoUri),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = "Add photo", tint = textMuted, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap to choose photo",
                color = textMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            DialogField("Name", name) { name = it }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    DialogField("Rank Title (e.g. Sgt.)", rankTitle) { rankTitle = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    DialogField("Rank Order (1=highest)", rankOrder) { rankOrder = it.filter { c -> c.isDigit() } }
                }
            }

            Spacer(Modifier.height(10.dp))
            DialogField("Serial (e.g. S-007)", serial) { serial = it }

            Spacer(Modifier.height(10.dp))
            DialogField("Role (e.g. Rifleman)", role) { role = it }
            Spacer(Modifier.height(10.dp))
            DialogField("Role (e.g. Rifleman)", role) { role = it }

            Spacer(Modifier.height(10.dp))                          // ← ADD THIS BLOCK
            Text("Blood Group", color = textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-").forEach { bg ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (bloodGroup == bg) Color(0xFFFF1744).copy(alpha = 0.15f) else bgDark,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (bloodGroup == bg) Color(0xFFFF1744) else borderDark,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { bloodGroup = bg }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(bg, color = if (bloodGroup == bg) Color(0xFFFF1744) else textMuted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Squad", color = textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquadState.squads.forEach { s ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (squad == s) accentBlue.copy(alpha = 0.15f) else bgDark,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (squad == s) accentBlue else borderDark,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { squad = s }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(s, color = if (squad == s) accentBlue else textMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(Color(0xFF0D2137), RoundedCornerShape(6.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("CANCEL", color = textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (name.isNotBlank()) {
                                val soldier = Soldier(
                                    id = existing?.id ?: "",
                                    name = name.trim(),
                                    rankTitle = rankTitle.trim(),
                                    rankOrder = rankOrder.toIntOrNull() ?: 4,
                                    serial = serial.trim(),
                                    squad = squad,
                                    role = role.trim(),
                                    hr = existing?.hr ?: 75,
                                    spo2 = existing?.spo2 ?: 98,
                                    temp = existing?.temp ?: 98.2f,
                                    battery = existing?.battery ?: 100,
                                    status = existing?.status ?: "stable",
                                    photoUri = photoUri
                                )
                                onSave(soldier)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SAVE", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DialogField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentBlue,
                unfocusedBorderColor = borderDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}