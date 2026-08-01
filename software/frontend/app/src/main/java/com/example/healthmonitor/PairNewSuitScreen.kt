package com.example.healthmonitor

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

// ── Design Tokens ──────────────────────────────────────────────────
private val bgDark         = Color(0xFF07111F)
private val cardDark       = Color(0xFF0D1B2A)
private val cardBorder     = Color(0xFF1B3A5C)
private val accentCyan     = Color(0xFF00E5FF)
private val statusGreen    = Color(0xFF00E676)
private val textPrimary    = Color(0xFFE2E8F0)
private val textSecondary  = Color(0xFF94A3B8)
private val textMuted      = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairNewSuitScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Mode: 0 = Existing Soldier, 1 = New Soldier
    var selectedTab by remember { mutableStateOf(0) }

    // State for Existing Soldier selection
    val soldiers = SoldierState.soldiers
    var searchQuery by remember { mutableStateOf("") }
    var selectedSoldierId by remember { mutableStateOf<String?>(soldiers.firstOrNull()?.id) }

    // State for New Soldier registration
    var newName by remember { mutableStateOf("") }
    var newRankTitle by remember { mutableStateOf("Pvt") }
    var newSerial by remember { mutableStateOf("SOLDIER-00${(soldiers.size + 1)}") }
    var newSquad by remember { mutableStateOf("Alpha") }
    var newRole by remember { mutableStateOf("Infantry") }
    var newBloodGroup by remember { mutableStateOf("O+") }

    // State for Suit Hardware ID
    var suitIdInput by remember { mutableStateOf("SUIT-101") }

    // State for Wireless / BLE Provisioning
    var wifiSsid by remember { mutableStateOf("Tactical_Net_Alpha") }
    var wifiPassword by remember { mutableStateOf("TacticalPass2026") }
    var backendHost by remember { mutableStateOf("192.168.1.50") }
    var backendPort by remember { mutableStateOf("8000") }

    // Execution & Feedback State
    var isPairing by remember { mutableStateOf(false) }
    var pairSuccessResult by remember { mutableStateOf<Soldier?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredSoldiers = remember(soldiers.toList(), searchQuery) {
        if (searchQuery.isBlank()) soldiers
        else soldiers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.serial.contains(searchQuery, ignoreCase = true) ||
            it.rankTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Header ──────────────────────────────────────────────
        HeaderSection()

        Spacer(Modifier.height(16.dp))

        // ── Main Layout: Split into Assignment Form & Provisioning Card ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Soldier Selection / Registration (Weight 1.2)
            Column(modifier = Modifier.weight(1.2f)) {
                TabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        pairSuccessResult = null
                        errorMessage = null
                    }
                )

                Spacer(Modifier.height(12.dp))

                AnimatedContent(targetState = selectedTab, label = "tabTransition") { tab ->
                    if (tab == 0) {
                        // Existing Soldier Picker
                        ExistingSoldierPicker(
                            soldiers = filteredSoldiers,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            selectedId = selectedSoldierId,
                            onSelect = { selectedSoldierId = it }
                        )
                    } else {
                        // New Soldier Registration Form
                        NewSoldierForm(
                            name = newName, onNameChange = { newName = it },
                            rank = newRankTitle, onRankChange = { newRankTitle = it },
                            serial = newSerial, onSerialChange = { newSerial = it },
                            squad = newSquad, onSquadChange = { newSquad = it },
                            role = newRole, onRoleChange = { newRole = it },
                            bloodGroup = newBloodGroup, onBloodGroupChange = { newBloodGroup = it }
                        )
                    }
                }
            }

            // Right Column: Suit ID & Wireless Provisioning (Weight 1.0)
            Column(modifier = Modifier.weight(1.0f)) {
                // Suit Hardware Identification
                SuitHardwareCard(
                    suitId = suitIdInput,
                    onSuitIdChange = { suitIdInput = it.uppercase() }
                )

                Spacer(Modifier.height(12.dp))

                // Wireless Network / BLE Settings
                ProvisioningSettingsCard(
                    ssid = wifiSsid, onSsidChange = { wifiSsid = it },
                    password = wifiPassword, onPasswordChange = { wifiPassword = it },
                    host = backendHost, onHostChange = { backendHost = it },
                    port = backendPort, onPortChange = { backendPort = it }
                )

                Spacer(Modifier.height(16.dp))

                // Error Banner if any
                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1219)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF1744))
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = textPrimary, fontSize = 13.sp)
                        }
                    }
                }

                // Action Pair Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isPairing = true
                            errorMessage = null
                            pairSuccessResult = null

                            val cleanSuitId = suitIdInput.trim().uppercase()
                            if (cleanSuitId.isBlank()) {
                                errorMessage = "Please enter a valid Suit Hardware ID"
                                isPairing = false
                                return@launch
                            }

                            val result: Soldier? = if (selectedTab == 0) {
                                // Pair existing
                                if (selectedSoldierId == null) {
                                    errorMessage = "Please select an existing soldier to pair."
                                    isPairing = false
                                    return@launch
                                }
                                ApiService.pairSuit(suitId = cleanSuitId, soldierId = selectedSoldierId)
                            } else {
                                // Register new soldier & pair
                                if (newName.isBlank() || newSerial.isBlank()) {
                                    errorMessage = "Please fill in soldier Name and Serial Number."
                                    isPairing = false
                                    return@launch
                                }

                                val squadId = SquadState.squadMap[newSquad] ?: "squad-alpha"
                                val newSoldierJson = JSONObject().apply {
                                    put("name", newName.trim())
                                    put("rank_title", newRankTitle)
                                    put("rank_order", getRankOrder(newRankTitle))
                                    put("serial", newSerial.trim().uppercase())
                                    put("squad_id", squadId)
                                    put("role", newRole)
                                    put("blood_group", newBloodGroup)
                                    put("status", "stable")
                                }
                                ApiService.pairSuit(suitId = cleanSuitId, newSoldier = newSoldierJson)
                            }

                            isPairing = false
                            if (result != null) {
                                pairSuccessResult = result
                                // Update local state list
                                val existingIdx = SoldierState.soldiers.indexOfFirst { it.id == result.id }
                                if (existingIdx != -1) {
                                    SoldierState.soldiers[existingIdx] = result
                                } else {
                                    SoldierState.soldiers.add(result)
                                }
                                Toast.makeText(context, "Suit ${result.suitId} paired successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                errorMessage = "Pairing failed. Ensure backend server is reachable."
                            }
                        }
                    },
                    enabled = !isPairing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentCyan,
                        contentColor = Color.Black
                    )
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("TRANSMITTING CONFIG...", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    } else {
                        Icon(Icons.Default.BluetoothConnected, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("PAIR SUIT & BROADCAST CONFIG", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp)
                    }
                }

                // Success Result Banner
                pairSuccessResult?.let { paired ->
                    Spacer(Modifier.height(16.dp))
                    PairingSuccessCard(pairedSoldier = paired)
                }
            }
        }
    }
}

// ── Header Component ───────────────────────────────────────────────
@Composable
private fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardDark, shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = cardBorder, shape = RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Brush.radialGradient(listOf(accentCyan.copy(alpha = 0.3f), Color.Transparent)), shape = CircleShape)
                .border(1.dp, accentCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null, tint = accentCyan, modifier = Modifier.size(26.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PAIR NEW WEARABLE SUIT",
                color = textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Assign hardware suit IDs to operatives & sync network/BLE config",
                color = textSecondary,
                fontSize = 12.sp
            )
        }

        // Status Badge
        Surface(
            color = statusGreen.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusGreen.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(statusGreen, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("BLE READY", color = statusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Mode Tab Selector ──────────────────────────────────────────────
@Composable
private fun TabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF040A14), shape = RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        val options = listOf("CHOOSE EXISTING SOLDIER", "REGISTER NEW SOLDIER")
        options.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) cardBorder else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) accentCyan else textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ── Existing Soldier Picker ────────────────────────────────────────
@Composable
private fun ExistingSoldierPicker(
    soldiers: List<Soldier>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search soldier name, serial, or rank...", color = textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentCyan,
                    unfocusedBorderColor = cardBorder,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (soldiers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("No matching personnel found", color = textMuted, fontSize = 13.sp)
                }
            } else {
                Box(modifier = Modifier.heightIn(max = 320.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(soldiers, key = { it.id }) { soldier ->
                            val isSelected = soldier.id == selectedId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) cardBorder.copy(alpha = 0.8f) else Color(0xFF0A1628))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) accentCyan else cardBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelect(soldier.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar circle
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF162D4A), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = soldier.rankTitle,
                                        color = accentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(soldier.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Serial: ${soldier.serial} • Squad: ${soldier.squad}", color = textSecondary, fontSize = 12.sp)
                                }

                                // Suit Status Tag
                                Surface(
                                    color = if (soldier.suitId != null) statusGreen.copy(alpha = 0.15f) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = soldier.suitId ?: "UNPAIRED",
                                        color = if (soldier.suitId != null) statusGreen else textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── New Soldier Form ───────────────────────────────────────────────
@Composable
private fun NewSoldierForm(
    name: String, onNameChange: (String) -> Unit,
    rank: String, onRankChange: (String) -> Unit,
    serial: String, onSerialChange: (String) -> Unit,
    squad: String, onSquadChange: (String) -> Unit,
    role: String, onRoleChange: (String) -> Unit,
    bloodGroup: String, onBloodGroupChange: (String) -> Unit
) {
    val ranks = listOf("Pvt", "Cpl", "Sgt", "Lt", "Capt", "Maj", "Col")
    val squads = listOf("Alpha", "Bravo", "Charlie", "Delta")
    val roles = listOf("Infantry", "Medic", "Squad Leader", "Recon", "Sniper", "Engineer")
    val bloodGroups = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

    Card(
        colors = CardDefaults.cardColors(containerColor = cardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("REGISTER NEW OPERATIVE", color = accentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

            // Name & Rank
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("Full Name", color = textMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )

                // Dropdown Rank Selector
                var rankExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = rank, onValueChange = {}, readOnly = true,
                        label = { Text("Rank", color = textMuted) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { rankExpanded = true }) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                    )
                    DropdownMenu(expanded = rankExpanded, onDismissRequest = { rankExpanded = false }) {
                        ranks.forEach { r ->
                            DropdownMenuItem(text = { Text(r) }, onClick = { onRankChange(r); rankExpanded = false })
                        }
                    }
                }
            }

            // Serial Number & Squad
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serial, onValueChange = onSerialChange,
                    label = { Text("Serial Number", color = textMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )

                var squadExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = squad, onValueChange = {}, readOnly = true,
                        label = { Text("Squad", color = textMuted) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { squadExpanded = true }) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                    )
                    DropdownMenu(expanded = squadExpanded, onDismissRequest = { squadExpanded = false }) {
                        squads.forEach { sq ->
                            DropdownMenuItem(text = { Text(sq) }, onClick = { onSquadChange(sq); squadExpanded = false })
                        }
                    }
                }
            }

            // Role & Blood Group
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var roleExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = role, onValueChange = {}, readOnly = true,
                        label = { Text("Tactical Role", color = textMuted) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { roleExpanded = true }) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                    )
                    DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        roles.forEach { ro ->
                            DropdownMenuItem(text = { Text(ro) }, onClick = { onRoleChange(ro); roleExpanded = false })
                        }
                    }
                }

                var bgExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = bloodGroup, onValueChange = {}, readOnly = true,
                        label = { Text("Blood Type", color = textMuted) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { bgExpanded = true }) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                    )
                    DropdownMenu(expanded = bgExpanded, onDismissRequest = { bgExpanded = false }) {
                        bloodGroups.forEach { bg ->
                            DropdownMenuItem(text = { Text(bg) }, onClick = { onBloodGroupChange(bg); bgExpanded = false })
                        }
                    }
                }
            }
        }
    }
}

// ── Suit Hardware Identification Card ──────────────────────────────
@Composable
private fun SuitHardwareCard(suitId: String, onSuitIdChange: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = accentCyan)
                Spacer(Modifier.width(8.dp))
                Text("SUIT HARDWARE IDENTIFIER", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = suitId,
                onValueChange = onSuitIdChange,
                label = { Text("Suit Hardware Serial / ID", color = textMuted) },
                leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = accentCyan) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder,
                    focusedTextColor = textPrimary, unfocusedTextColor = textPrimary
                )
            )

            Spacer(Modifier.height(8.dp))

            // Quick Pick Presets
            Text("Quick Presets:", color = textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SUIT-101", "SUIT-102", "SUIT-103", "SUIT-201").forEach { preset ->
                    AssistChip(
                        onClick = { onSuitIdChange(preset) },
                        label = { Text(preset, fontSize = 11.sp, color = textPrimary) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F243A))
                    )
                }
            }
        }
    }
}

// ── Wireless & BLE Provisioning Settings Card ──────────────────────
@Composable
private fun ProvisioningSettingsCard(
    ssid: String, onSsidChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    host: String, onHostChange: (String) -> Unit,
    port: String, onPortChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = statusGreen)
                Spacer(Modifier.width(8.dp))
                Text("WIRELESS BLE/WIFI PROVISIONING", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedTextField(
                value = ssid, onValueChange = onSsidChange,
                label = { Text("WiFi Network SSID", color = textMuted) },
                leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = textMuted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
            )

            OutlinedTextField(
                value = password, onValueChange = onPasswordChange,
                label = { Text("WiFi Password", color = textMuted) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = textMuted) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = onHostChange,
                    label = { Text("Backend Host / IP", color = textMuted) },
                    singleLine = true, modifier = Modifier.weight(1.5f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )

                OutlinedTextField(
                    value = port, onValueChange = onPortChange,
                    label = { Text("Port", color = textMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentCyan, unfocusedBorderColor = cardBorder, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )
            }
        }
    }
}

// ── Success Confirmation Banner ────────────────────────────────────
@Composable
private fun PairingSuccessCard(pairedSoldier: Soldier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF082D1E)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, statusGreen),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("SUIT PAIRING SUCCESSFUL", color = statusGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            Text("Operative: ${pairedSoldier.rankTitle} ${pairedSoldier.name} (${pairedSoldier.serial})", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Assigned Suit Hardware ID: ${pairedSoldier.suitId}", color = accentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Squad: ${pairedSoldier.squad} • Status: ${pairedSoldier.status.uppercase()}", color = textSecondary, fontSize = 12.sp)

            Spacer(Modifier.height(8.dp))
            Text("Suit firmware will now fetch commands and send vitals using suit identifier '${pairedSoldier.suitId}'.", color = textMuted, fontSize = 11.sp)
        }
    }
}

private fun getRankOrder(rankTitle: String): Int {
    return when (rankTitle) {
        "Pvt"  -> 1
        "Cpl"  -> 2
        "Sgt"  -> 3
        "Lt"   -> 4
        "Capt" -> 5
        "Maj"  -> 6
        "Col"  -> 7
        else   -> 1
    }
}
