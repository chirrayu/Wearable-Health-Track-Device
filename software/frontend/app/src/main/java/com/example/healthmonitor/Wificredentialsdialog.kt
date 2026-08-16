package com.example.healthmonitor

/*
 * Standalone Compose dialog — doesn't touch any existing screen files.
 * Wire it in by calling WifiCredentialsDialog(...) from wherever you
 * want the "Switch to WiFi" action to live (e.g. a settings screen or
 * a button next to the connection status indicator).
 *
 * Usage:
 *
 *   var showWifiDialog by remember { mutableStateOf(false) }
 *
 *   if (showWifiDialog) {
 *       WifiCredentialsDialog(
 *           onDismiss = { showWifiDialog = false },
 *           onSubmit = { ssid, password ->
 *               val sent = BleManager.sendWifiCredentials(ssid, password)
 *               if (!sent) {
 *                   // show a snackbar/toast — BLE not connected
 *               }
 *               showWifiDialog = false
 *           }
 *       )
 *   }
 *
 * Only show the trigger button when BleManager.canProvisionWifi()
 * is true (i.e. BLE is actually connected to the suit).
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun WifiCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (ssid: String, password: String) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect suit to WiFi") },
        text = {
            Column {
                Text(
                    "Sent over the existing Bluetooth link to the suit. " +
                            "It will switch to WiFi mode once connected.",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("WiFi network name (SSID)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(ssid.trim(), password) },
                enabled = ssid.isNotBlank()
            ) {
                Text("Send to suit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}