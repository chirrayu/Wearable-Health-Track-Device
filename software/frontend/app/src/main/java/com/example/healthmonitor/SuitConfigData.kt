/*This file mirrors backend/suit_config.py — per-soldier sensor, sampling and
  communication settings for the wearable suit. Field names match the
  SuitConfigOut / SuitConfigUpdate schemas so this can be swapped for a real
  network call later without changing the screen.*/
package com.example.healthmonitor

import androidx.compose.runtime.mutableStateMapOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SuitConfig(
    var hrSensor: Boolean = true,
    var spo2Sensor: Boolean = true,
    var tempSensor: Boolean = true,
    var accelerometer: Boolean = true,
    var gpsEnabled: Boolean = true,
    var samplingRateSecs: Int = 5,
    var wifiEnabled: Boolean = true,
    var meshEnabled: Boolean = true,
    var radioGateway: Boolean = false,
    var emergencyMode: Boolean = false,
    var updatedAt: Long = System.currentTimeMillis()
)

fun SuitConfig.formattedUpdatedAt(): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(updatedAt))
}

// Factory defaults — matches the /suit/{id}/reset endpoint on the backend.
fun defaultSuitConfig(): SuitConfig = SuitConfig(
    hrSensor = true,
    spo2Sensor = true,
    tempSensor = true,
    accelerometer = true,
    gpsEnabled = true,
    samplingRateSecs = 5,
    wifiEnabled = true,
    meshEnabled = true,
    radioGateway = false,
    emergencyMode = false,
    updatedAt = System.currentTimeMillis()
)

object SuitConfigState {

    // soldierId -> SuitConfig. Backed by a state map so screens recompose
    // whenever a config is added/replaced.
    val configs = mutableStateMapOf<String, SuitConfig>()

    fun getConfig(soldierId: String): SuitConfig =
        configs.getOrPut(soldierId) { defaultSuitConfig() }

    fun saveConfig(soldierId: String, config: SuitConfig) {
        config.updatedAt = System.currentTimeMillis()
        configs[soldierId] = config
    }

    fun resetConfig(soldierId: String): SuitConfig {
        val fresh = defaultSuitConfig()
        configs[soldierId] = fresh
        return fresh
    }

    fun setEmergencyMode(soldierId: String, enabled: Boolean) {
        val current = getConfig(soldierId).copy()
        current.emergencyMode = enabled
        current.updatedAt = System.currentTimeMillis()
        configs[soldierId] = current
    }
}
