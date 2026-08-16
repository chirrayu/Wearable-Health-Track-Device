package com.example.healthmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    wifiEnabled: Boolean,
    onWifiToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {

        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CardDark
            )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = "ESP32 Wi-Fi",
                        color = Color.White
                    )

                    Text(
                        text = if (wifiEnabled)
                            "Enabled"
                        else
                            "Disabled",
                        color = TextMuted
                    )
                }

                Switch(
                    checked = wifiEnabled,
                    onCheckedChange = onWifiToggle
                )
            }
        }
    }
}