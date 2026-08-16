/*This file mirrors backend/suit_config.py — per-soldier sensor, sampling and
  communication settings for the wearable suit. Field names match the
  SuitConfigOut / SuitConfigUpdate schemas.

  ⚠ CHANGED — previously every function here only touched a local
  in-memory map (configs), so the "SAVE CONFIG" button in
  ConfigureSuitScreen showed a green "Saved" banner without ever telling
  the backend, which meant the ESP32 firmware polling
  GET /suit/{soldier_id}/commands never saw any of it. All mutating
  functions are now suspend functions that call the real
  backend/suit_config.py endpoints via ApiService, and only update the
  local cache with what the server actually confirmed.*/
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

// Local-only fallback shown before the real config has loaded from the
// backend — matches database.py's SuitConfigModel column defaults, so it
// looks right even in the split second before the real fetch completes.
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

    // soldierId -> SuitConfig, populated only from real backend responses
    // (loadConfig/saveConfig/resetConfig/setEmergencyMode below), never
    // written to directly from the UI anymore.
    val configs = mutableStateMapOf<String, SuitConfig>()

    // soldierId -> whether a fetch/save is currently in flight, so the
    // UI can show a loading state instead of stale/default data.
    val loading = mutableStateMapOf<String, Boolean>()

    // Returns the cached config if we have one, or the visual default
    // while the real fetch is still in flight. Call loadConfig() first
    // to actually populate this from the backend.
    fun getConfig(soldierId: String): SuitConfig =
        configs[soldierId] ?: defaultSuitConfig()

    // ⚠ NEW — actually fetches the soldier's real config from the
    // backend (GET /suit/{id}, auto-creating one server-side if it
    // doesn't exist yet). Call this when a soldier is selected.
    suspend fun loadConfig(soldierId: String): SuitConfig? {
        loading[soldierId] = true
        val real = ApiService.getSuitConfig(soldierId)
        loading[soldierId] = false
        if (real != null) {
            configs[soldierId] = real
        }
        return real
    }

    // ⚠ CHANGED — now calls PUT /suit/{id} for real instead of only
    // writing to the local map. Only updates the cache with what the
    // server actually confirmed back.
    suspend fun saveConfig(soldierId: String, config: SuitConfig): Boolean {
        val updated = ApiService.updateSuitConfig(soldierId, config)
        return if (updated != null) {
            configs[soldierId] = updated
            true
        } else {
            false
        }
    }

    // ⚠ CHANGED — now calls POST /suit/{id}/reset for real.
    suspend fun resetConfig(soldierId: String): SuitConfig? {
        val fresh = ApiService.resetSuitConfig(soldierId)
        if (fresh != null) {
            configs[soldierId] = fresh
        }
        return fresh
    }

    // ⚠ CHANGED — now calls POST /suit/{id}/emergency for real, which is
    // important: the backend endpoint also marks the soldier CRITICAL
    // and fires a real alert through evaluate_and_create_alerts(). The
    // soldier's status will update through the normal WebSocket
    // snapshot/vitals_update flow — this no longer fakes it locally via
    // SoldierState.updateSoldier() the way the old code did.
    suspend fun setEmergencyMode(soldierId: String, enabled: Boolean): Boolean {
        val updated = ApiService.setEmergencyMode(soldierId, enabled)
        return if (updated != null) {
            configs[soldierId] = updated
            true
        } else {
            false
        }
    }
}