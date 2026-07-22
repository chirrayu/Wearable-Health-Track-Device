package com.example.healthmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val bgDark      = Color(0xFF07111F)
private val cardDark    = Color(0xFF081B33)
private val borderDark  = Color(0xFF1A3A5C)
private val textMuted   = Color(0xFF6B7F99)
private val accentBlue  = Color(0xFF00C2FF)
private val accentGreen = Color(0xFF00E676)
private val critRed     = Color(0xFFFF445A)


@Composable
fun ConfigureSuitScreen() {

    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(20.dp)
    ) {

        ConnectionStatusBar()

        Spacer(Modifier.height(16.dp))

        statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentGreen.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, accentGreen.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg, color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Sensors panel ──────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(cardDark, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .border(1.dp, borderDark, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Text("SENSORS", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(14.dp))

                ToggleRow("Heart Rate Sensor", SuitConfig.heartRateSensorEnabled.value, accentGreen) {
                    SuitConfig.heartRateSensorEnabled.value = it
                }
                SuitDivider()
                ToggleRow("SpO\u2082 Sensor", SuitConfig.spo2SensorEnabled.value, accentGreen) {
                    SuitConfig.spo2SensorEnabled.value = it
                }
                SuitDivider()
                ToggleRow("Temperature Sensor", SuitConfig.tempSensorEnabled.value, accentGreen) {
                    SuitConfig.tempSensorEnabled.value = it
                }
                SuitDivider()
                ToggleRow("Accelerometer", SuitConfig.accelerometerEnabled.value, accentGreen) {
                    SuitConfig.accelerometerEnabled.value = it
                }
                SuitDivider()
                ToggleRow("GPS", SuitConfig.gpsEnabled.value, accentGreen) {
                    SuitConfig.gpsEnabled.value = it
                }

                Spacer(Modifier.height(16.dp))

                Text("Sampling Rate", color = textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SamplingRateDropdown()
            }

            // ── Communication panel ───────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(cardDark, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .border(1.dp, borderDark, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Text("COMMUNICATION", color = accentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(14.dp))

                ToggleRow("WiFi", SuitConfig.wifiEnabled.value, accentBlue) {
                    SuitConfig.wifiEnabled.value = it
                }
                SuitDivider()
                ToggleRow("Mesh Network", SuitConfig.meshNetworkEnabled.value, accentBlue) {
                    SuitConfig.meshNetworkEnabled.value = it
                }
                SuitDivider()
                ToggleRow("Radio Gateway", SuitConfig.radioGatewayEnabled.value, accentBlue) {
                    SuitConfig.radioGatewayEnabled.value = it
                }

                Spacer(Modifier.height(20.dp))

                Text("EMERGENCY MODE", color = critRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    label = if (SuitConfig.emergencyMode.value) "ACTIVE" else "STANDBY",
                    checked = SuitConfig.emergencyMode.value,
                    activeColor = critRed
                ) { enabled ->
                    SuitConfig.emergencyMode.value = enabled
                    SuitConfigApplier.applyEmergencyMode(
                        soldierId = SuitConfig.selectedSoldierId.value,
                        enabled = enabled,
                        notify = { alert ->
                            NotificationHelper.sendAlertNotification(context, alert)
                        }
                    )
                    statusMessage = if (enabled)
                        "Emergency mode activated for selected soldier"
                    else
                        "Emergency mode deactivated"
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Action buttons ────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Save Configuration
            Row(
                modifier = Modifier
                    .background(accentGreen.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, accentGreen.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable {
                        SuitConfigApplier.applySensorConfig(SuitConfig.selectedSoldierId.value)
                        SuitConfigApplier.applyCommsConfig()
                        statusMessage = "Configuration saved and applied to soldier data"
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = accentGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Configuration", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Reset
            Row(
                modifier = Modifier
                    .background(cardDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, borderDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable {
                        SuitConfigApplier.resetToDefaults()
                        statusMessage = "Configuration reset to defaults"
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset", color = textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Test Sensors
            Row(
                modifier = Modifier
                    .background(accentBlue.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, accentBlue.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable { testing = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = "Test", tint = accentBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (testing) "Testing..." else "Test Sensors",
                    color = accentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (testing) {
            LaunchedEffect(Unit) {
                delay(1500)
                testing = false
                statusMessage = buildString {
                    append("Test complete — ")
                    append(if (SuitConfig.heartRateSensorEnabled.value) "HR ok, " else "HR off, ")
                    append(if (SuitConfig.spo2SensorEnabled.value) "SpO2 ok, " else "SpO2 off, ")
                    append(if (SuitConfig.tempSensorEnabled.value) "Temp ok, " else "Temp off, ")
                    append(if (SuitConfig.gpsEnabled.value) "GPS ok" else "GPS off")
                }
            }
        }
    }
}


// ── Connection status bar ─────────────────────────────────────────
@Composable
fun ConnectionStatusBar() {

    val state by SuitConfig.connectionState
    val deviceName by SuitConfig.pairedDeviceName
    var connecting by remember { mutableStateOf(false) }

    val (color, label) = when (state) {
        SuitConnectionState.CONNECTED    -> accentGreen to "Connected${deviceName?.let { " — $it" } ?: ""}"
        SuitConnectionState.CONNECTING   -> accentBlue  to "Connecting..."
        SuitConnectionState.ERROR        -> critRed     to "Connection error"
        SuitConnectionState.DISCONNECTED -> textMuted   to "Disconnected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .border(1.dp, borderDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.weight(1f))

        if (state != SuitConnectionState.CONNECTED) {
            Box(
                modifier = Modifier
                    .background(accentBlue.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable(enabled = !connecting) { connecting = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (connecting) "Pairing..." else "Pair Suit",
                    color = accentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .background(critRed.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable { SuitConnectionProvider.connection.disconnect() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Disconnect", color = critRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (connecting) {
        LaunchedEffect(Unit) {
            delay(1200)
            SuitConnectionProvider.connection.connect(
                "Suit-Unit-${SuitConfig.selectedSoldierId.value}"
            ) {
                connecting = false
            }
        }
    }
}


// ── Reusable toggle row ───────────────────────────────────────────
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    activeColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = activeColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = borderDark
            )
        )
    }
}

@Composable
fun SuitDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(borderDark.copy(alpha = 0.5f))
    )
}


// ── Sampling rate dropdown ────────────────────────────────────────
@Composable
fun SamplingRateDropdown() {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(1, 5, 10, 30, 60)
    val current = SuitConfig.samplingRateSeconds.value

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .border(1.dp, borderDark, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$current seconds", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ExpandMore, contentDescription = "Expand", tint = textMuted, modifier = Modifier.size(18.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(cardDark)
        ) {
            options.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text("$seconds seconds", color = Color.White) },
                    onClick = {
                        SuitConfig.samplingRateSeconds.value = seconds
                        expanded = false
                    }
                )
            }
        }
    }
}