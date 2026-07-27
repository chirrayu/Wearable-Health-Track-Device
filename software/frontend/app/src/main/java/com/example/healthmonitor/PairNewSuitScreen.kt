/*Pair New Suit — a Bluetooth-style scan/pair flow. Since there's no real BLE
  stack wired up yet, "discovered" suits are a static mock list; picking one
  and confirming creates a SuitConfig entry for the chosen soldier (via
  SuitConfigState, the same store ConfigureSuitScreen reads/writes) so a
  freshly-paired suit immediately shows up as "configured" there.*/
package com.example.healthmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class DiscoveredSuit(
    val suitSerial: String,
    val signalPercent: Int,
    val battery: Int,
    val firmware: String = "v2.4.1"
)

private fun mockScanResults(alreadyPaired: Set<String>): List<DiscoveredSuit> = listOf(
    DiscoveredSuit("SUIT-2201", 92, 88),
    DiscoveredSuit("SUIT-2202", 76, 64),
    DiscoveredSuit("SUIT-2205", 58, 41),
    DiscoveredSuit("SUIT-2209", 34, 97),
    DiscoveredSuit("SUIT-2214", 20, 12),
).filterNot { it.suitSerial in alreadyPaired }

@Composable
fun PairNewSuitScreen() {

    // soldierId -> suit serial it's paired to (local mock pairing registry).
    val pairedSuits = remember { mutableStateMapOf<String, String>() }

    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<DiscoveredSuit>>(emptyList()) }
    var selectedSuit by remember { mutableStateOf<DiscoveredSuit?>(null) }
    var selectedSoldierId by remember { mutableStateOf<String?>(null) }
    var justPaired by remember { mutableStateOf<Pair<Soldier, DiscoveredSuit>?>(null) }

    fun startScan() {
        isScanning = true
        scanResults = emptyList()
        selectedSuit = null
    }

    if (isScanning) {
        LaunchedEffect(Unit) {
            delay(1400)
            scanResults = mockScanResults(pairedSuits.values.toSet())
            isScanning = false
        }
    }

    LaunchedEffect(Unit) { startScan() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(20.dp)
    ) {
        Text(
            text = "PAIR NEW SUIT",
            color = textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Connect a wearable suit to a soldier profile",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left: scan results ─────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = accentBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "NEARBY SUITS",
                            color = textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .background(borderDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isScanning) { startScan() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = if (isScanning) textMuted else accentBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isScanning) "SCANNING..." else "RESCAN",
                            color = if (isScanning) textMuted else accentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning for nearby suits…", color = textMuted, fontSize = 13.sp)
                        }
                    }
                } else if (scanResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No new suits found nearby", color = textMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(scanResults, key = { it.suitSerial }) { suit ->
                            SuitDiscoveryRow(
                                suit = suit,
                                selected = suit.suitSerial == selectedSuit?.suitSerial,
                                onClick = { selectedSuit = suit }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(20.dp))

            // ── Right: assign to soldier + confirm ─────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(cardDark, RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                if (justPaired != null) {
                    val (soldier, suit) = justPaired!!
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusGreen, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Paired successfully", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${suit.suitSerial} → ${soldier.rankTitle} ${soldier.name}",
                                color = textMuted,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, accentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        justPaired = null
                                        selectedSuit = null
                                        selectedSoldierId = null
                                        startScan()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text("PAIR ANOTHER", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        "ASSIGN TO SOLDIER",
                        color = textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    if (selectedSuit == null) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("Select a suit from the left to continue", color = textMuted, fontSize = 13.sp)
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(SoldierState.soldiers, key = { it.id }) { soldier ->
                                    val alreadyHasSuit = pairedSuits.containsKey(soldier.id)
                                    SoldierAssignRow(
                                        soldier = soldier,
                                        selected = soldier.id == selectedSoldierId,
                                        alreadyPaired = alreadyHasSuit,
                                        onClick = { if (!alreadyHasSuit) selectedSoldierId = soldier.id }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val soldier = SoldierState.soldiers.find { it.id == selectedSoldierId }
                        val canConfirm = soldier != null

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (canConfirm) statusGreen.copy(alpha = 0.15f) else borderDark.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (canConfirm) statusGreen.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = canConfirm) {
                                    val s = soldier ?: return@clickable
                                    val suit = selectedSuit ?: return@clickable
                                    pairedSuits[s.id] = suit.suitSerial
                                    SuitConfigState.resetConfig(s.id) // seed default config on pairing
                                    justPaired = s to suit
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BluetoothConnected,
                                    contentDescription = null,
                                    tint = if (canConfirm) statusGreen else textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "CONFIRM & PAIR",
                                    color = if (canConfirm) statusGreen else textMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pairedSuits.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "PAIRED THIS SESSION: " + pairedSuits.entries.joinToString(", ") { (soldierId, suitSerial) ->
                    val name = SoldierState.soldiers.find { it.id == soldierId }?.name ?: soldierId
                    "$suitSerial → $name"
                },
                color = textMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SuitDiscoveryRow(suit: DiscoveredSuit, selected: Boolean, onClick: () -> Unit) {
    val signalColor = when {
        suit.signalPercent >= 70 -> statusGreen
        suit.signalPercent >= 40 -> statusYellow
        else -> statusRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) accentBlue.copy(alpha = 0.15f) else cardDark,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (selected) accentBlue.copy(alpha = 0.5f) else borderDark,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = accentBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(suit.suitSerial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Firmware ${suit.firmware} · Battery ${suit.battery}%", color = textMuted, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = signalColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${suit.signalPercent}%", color = signalColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SoldierAssignRow(soldier: Soldier, selected: Boolean, alreadyPaired: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) accentBlue.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (selected) accentBlue.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !alreadyPaired) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                color = if (alreadyPaired) textMuted else Color.White,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(soldier.serial, color = textMuted, fontSize = 11.sp)
        }
        if (alreadyPaired) {
            Text("PAIRED", color = statusGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
