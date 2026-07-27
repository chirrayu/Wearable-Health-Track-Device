package com.example.healthmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// NOTE: bgDark, cardDark, borderDark, textMuted, accentBlue, statusGreen,
// statusYellow, statusRed, statusGray, and statusColor() are all declared
// (public) in SoldiersScreen.kt and shared across the app. Do NOT redeclare
// them here — that caused "Conflicting declarations" / overload-resolution
// ambiguity across this whole file.

// ── Triage helpers ────────────────────────────────────────────────
private fun triagePriority(status: String): Int = when (status) {
    "critical" -> 0
    "serious"  -> 1
    "offline"  -> 2
    else       -> 3
}

private fun triageScoreLabel(soldier: Soldier): String {
    val hr   = soldier.hr   ?: return "—"
    val spo2 = soldier.spo2 ?: return "—"
    val hrPenalty   = if (hr > 120 || hr < 50) 3 else if (hr > 100) 1 else 0
    val spo2Penalty = if (spo2 < 86) 3 else if (spo2 < 91) 2 else if (spo2 < 95) 1 else 0
    return (hrPenalty + spo2Penalty).toString()
}

// ── Casualty Queue Screen ─────────────────────────────────────────
@Composable
fun CasualtyQueueScreen() {

    var confirmTreat by remember { mutableStateOf<Soldier?>(null) }
    var confirmEvac  by remember { mutableStateOf<Soldier?>(null) }

    val queue = SoldierState.soldiers
        .filter { it.status != "stable" }
        .sortedBy { triagePriority(it.status) }

    val criticalCount = SoldierState.soldiers.count { it.status == "critical" }
    val seriousCount  = SoldierState.soldiers.count { it.status == "serious" }
    val offlineCount  = SoldierState.soldiers.count { it.status == "offline" }

    confirmTreat?.let { soldier ->
        AlertDialog(
            onDismissRequest = { confirmTreat = null },
            containerColor   = cardDark,
            icon  = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusGreen) },
            title = { Text("Mark as Treated?", color = Color.White) },
            text  = {
                Text(
                    "${soldier.rankTitle} ${soldier.name} will be moved out of the casualty queue and marked stable.",
                    color = textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SoldierState.updateSoldier(soldier.copy(status = "stable"))
                    confirmTreat = null
                }) { Text("Confirm", color = statusGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTreat = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    confirmEvac?.let { soldier ->
        AlertDialog(
            onDismissRequest = { confirmEvac = null },
            containerColor   = cardDark,
            icon  = { Icon(Icons.Default.Warning, contentDescription = null, tint = accentBlue) },
            title = { Text("Confirm Evacuation", color = Color.White) },
            text  = {
                Text(
                    "${soldier.rankTitle} ${soldier.name} (${soldier.serial}) will be flagged for evacuation and marked stable pending transport.",
                    color = textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SoldierState.updateSoldier(soldier.copy(status = "stable"))
                    confirmEvac = null
                }) { Text("Evacuate", color = accentBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEvac = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(20.dp)
    ) {
        Text(
            text = "CASUALTY QUEUE",
            color = textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Triage-ranked worklist, worst first",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QueueStatChip("CRITICAL", criticalCount, statusRed,    Modifier.weight(1f))
            QueueStatChip("SERIOUS",  seriousCount,  statusYellow, Modifier.weight(1f))
            QueueStatChip("OFFLINE",  offlineCount,  statusGray,   Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Queue is empty — every soldier is stable",
                        color = textMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(queue) { index, soldier ->
                    CasualtyQueueRow(
                        rank       = index + 1,
                        soldier    = soldier,
                        onTreat    = { confirmTreat = soldier },
                        onEvacuate = { confirmEvac  = soldier }
                    )
                }
            }
        }
    }
}


@Composable
fun QueueStatChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}


@Composable
fun CasualtyQueueRow(
    rank: Int,
    soldier: Soldier,
    onTreat: () -> Unit,
    onEvacuate: () -> Unit
) {
    val color = statusColor(soldier.status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardDark, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${soldier.rankTitle} ${soldier.name}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        soldier.status.uppercase(),
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                "${soldier.serial} · ${soldier.squad} · ${soldier.role} · ${soldier.bloodGroup}",
                color = textMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                VitalMini("HR",    soldier.hr?.let   { "$it bpm" } ?: "—", hrColor(soldier.hr))
                VitalMini("SpO₂", soldier.spo2?.let { "$it%"    } ?: "—", textMuted)
                VitalMini("Temp", soldier.temp?.let  { "${it}°F" } ?: "—", textMuted)
                VitalMini("Score", triageScoreLabel(soldier), color)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            SmallActionButton("TREAT",    statusGreen, onTreat)
            Spacer(Modifier.height(6.dp))
            SmallActionButton("EVACUATE", accentBlue,  onEvacuate)
        }
    }
}


@Composable
fun VitalMini(label: String, value: String, color: Color) {
    Column {
        Text(label, color = textMuted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}


@Composable
fun SmallActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}