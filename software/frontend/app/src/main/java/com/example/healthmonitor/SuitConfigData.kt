package com.example.healthmonitor

import androidx.compose.runtime.mutableStateOf

// ── Suit configuration state ──────────────────────────────────────
object SuitConfig {
    val heartRateSensorEnabled = mutableStateOf(true)
    val spo2SensorEnabled      = mutableStateOf(true)
    val tempSensorEnabled      = mutableStateOf(true)
    val accelerometerEnabled   = mutableStateOf(true)
    val gpsEnabled             = mutableStateOf(true)
    val samplingRateSeconds    = mutableStateOf(5)
    val wifiEnabled            = mutableStateOf(true)
    val meshNetworkEnabled     = mutableStateOf(true)
    val radioGatewayEnabled    = mutableStateOf(false)
    val emergencyMode          = mutableStateOf(false)
    val connectionState        = mutableStateOf(SuitConnectionState.DISCONNECTED)
    val pairedDeviceName       = mutableStateOf<String?>(null)
    val selectedSoldierId      = mutableStateOf(SoldierState.soldiers.firstOrNull()?.id ?: "")
}

// ── Connection states ─────────────────────────────────────────────
enum class SuitConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

// ── Transport interface ───────────────────────────────────────────
interface SuitConnection {
    fun connect(deviceName: String, onResult: (Boolean) -> Unit)
    fun disconnect()
    fun sendSensorToggle(sensor: String, enabled: Boolean)
    fun sendSamplingRate(seconds: Int)
    fun sendCommsToggle(channel: String, enabled: Boolean)
    fun sendEmergencyMode(enabled: Boolean)
}

// ── Simulated connection (swap for real BLE later) ────────────────
class SimulatedSuitConnection : SuitConnection {

    override fun connect(deviceName: String, onResult: (Boolean) -> Unit) {
        SuitConfig.connectionState.value = SuitConnectionState.CONNECTING
        SuitConfig.pairedDeviceName.value = deviceName
        SuitConfig.connectionState.value = SuitConnectionState.CONNECTED
        onResult(true)
    }

    override fun disconnect() {
        SuitConfig.connectionState.value = SuitConnectionState.DISCONNECTED
        SuitConfig.pairedDeviceName.value = null
    }

    override fun sendSensorToggle(sensor: String, enabled: Boolean) {}
    override fun sendSamplingRate(seconds: Int) {}
    override fun sendCommsToggle(channel: String, enabled: Boolean) {}
    override fun sendEmergencyMode(enabled: Boolean) {}
}

// ── Single access point ───────────────────────────────────────────
object SuitConnectionProvider {
    val connection: SuitConnection = SimulatedSuitConnection()
}

// ── Apply config to soldier data ──────────────────────────────────
object SuitConfigApplier {

    fun applySensorConfig(soldierId: String) {
        val soldier = SoldierState.soldiers.find { it.id == soldierId } ?: return
        val updated = soldier.copy(
            hr   = if (SuitConfig.heartRateSensorEnabled.value) soldier.hr else null,
            spo2 = if (SuitConfig.spo2SensorEnabled.value) soldier.spo2 else null,
            temp = if (SuitConfig.tempSensorEnabled.value) soldier.temp else null
        )
        SoldierState.updateSoldier(updated)
        SuitConnectionProvider.connection.sendSensorToggle("hr",            SuitConfig.heartRateSensorEnabled.value)
        SuitConnectionProvider.connection.sendSensorToggle("spo2",          SuitConfig.spo2SensorEnabled.value)
        SuitConnectionProvider.connection.sendSensorToggle("temp",          SuitConfig.tempSensorEnabled.value)
        SuitConnectionProvider.connection.sendSensorToggle("accelerometer", SuitConfig.accelerometerEnabled.value)
        SuitConnectionProvider.connection.sendSensorToggle("gps",           SuitConfig.gpsEnabled.value)
        SuitConnectionProvider.connection.sendSamplingRate(SuitConfig.samplingRateSeconds.value)
    }

    fun applyCommsConfig() {
        SuitConnectionProvider.connection.sendCommsToggle("wifi",          SuitConfig.wifiEnabled.value)
        SuitConnectionProvider.connection.sendCommsToggle("mesh",          SuitConfig.meshNetworkEnabled.value)
        SuitConnectionProvider.connection.sendCommsToggle("radio_gateway", SuitConfig.radioGatewayEnabled.value)
    }

    fun applyEmergencyMode(
        soldierId: String,
        enabled: Boolean,
        notify: (AppAlert) -> Unit
    ) {
        SuitConnectionProvider.connection.sendEmergencyMode(enabled)
        val soldier = SoldierState.soldiers.find { it.id == soldierId } ?: return

        if (enabled) {
            SoldierState.updateSoldier(soldier.copy(status = "critical"))
            AlertState.addAlert(
                title          = "Emergency Mode Activated",
                severity       = "critical",
                soldierName    = "${soldier.rankTitle} ${soldier.name}",
                soldierSerial  = soldier.serial,
                message        = "Manual emergency mode activated from suit configuration panel.",
                actionRequired = true,
                notify         = notify
            )
        }
    }

    fun resetToDefaults() {
        SuitConfig.heartRateSensorEnabled.value = true
        SuitConfig.spo2SensorEnabled.value      = true
        SuitConfig.tempSensorEnabled.value      = true
        SuitConfig.accelerometerEnabled.value   = true
        SuitConfig.gpsEnabled.value             = true
        SuitConfig.samplingRateSeconds.value    = 5
        SuitConfig.wifiEnabled.value            = true
        SuitConfig.meshNetworkEnabled.value     = true
        SuitConfig.radioGatewayEnabled.value    = false
        SuitConfig.emergencyMode.value          = false
    }
}