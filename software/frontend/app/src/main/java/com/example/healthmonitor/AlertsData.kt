package com.example.healthmonitor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

// ── Alert model ───────────────────────────────────────────────────
data class AppAlert(
    val id: String,
    val title: String,
    val severity: String,       // "critical" | "warning" | "information"
    val soldierName: String,
    val soldierSerial: String,
    val message: String,
    val timestamp: Long,
    val actionRequired: Boolean = false
)

// ── No-movement threshold options ─────────────────────────────────
object NoMovementSettings {
    val thresholdMinutes = mutableStateOf(30)
}

// ── Shared alerts list + rules engine ─────────────────────────────
object AlertState {

    val alerts = mutableStateListOf<AppAlert>()

    private var nextId = 1

    private val firedRules = mutableSetOf<String>()

    fun unreadCount(): Int = alerts.count { it.severity == "critical" || it.severity == "information" }

    fun addAlert(
        title: String,
        severity: String,
        soldierName: String,
        soldierSerial: String,
        message: String,
        actionRequired: Boolean = false,
        notify: (AppAlert) -> Unit = {}
    ) {
        val alert = AppAlert(
            id = (nextId++).toString(),
            title = title,
            severity = severity,
            soldierName = soldierName,
            soldierSerial = soldierSerial,
            message = message,
            timestamp = System.currentTimeMillis(),
            actionRequired = actionRequired
        )
        alerts.add(0, alert)

        if (severity == "critical") {
            AppState.criticalCount.value += 1
        }
        AppState.alertCount.value += 1

        notify(alert)
    }

    fun clearAll() {
        alerts.clear()
        firedRules.clear()
        AppState.alertCount.value = 0
        AppState.criticalCount.value = 0
    }

    fun markAllRead() {
        AppState.alertCount.value = 0
    }

    fun evaluateRules(soldier: Soldier, notify: (AppAlert) -> Unit) {

        val name = "${soldier.rankTitle} ${soldier.name}"
        val serial = soldier.serial

        soldier.hr?.let { hr ->
            val key = "${soldier.id}_fast_hr"
            if (hr > 130 && firedRules.add(key)) {
                addAlert(
                    title = "Very Fast Heartbeat",
                    severity = "critical",
                    soldierName = name,
                    soldierSerial = serial,
                    message = "Heart rate $hr BPM. Immediate intervention may be required.",
                    actionRequired = true,
                    notify = notify
                )
            } else if (hr <= 130) {
                firedRules.remove(key)
            }
        }

        soldier.spo2?.let { spo2 ->
            val key = "${soldier.id}_low_spo2"
            if (spo2 < 90 && firedRules.add(key)) {
                addAlert(
                    title = "Low Blood Oxygen",
                    severity = "critical",
                    soldierName = name,
                    soldierSerial = serial,
                    message = "SpO2 dropped to $spo2%. Immediate intervention may be required.",
                    actionRequired = true,
                    notify = notify
                )
            } else if (spo2 >= 90) {
                firedRules.remove(key)
            }
        }

        if (soldier.status == "offline") {
            val key = "${soldier.id}_no_movement"
            if (firedRules.add(key)) {
                addAlert(
                    title = "No Movement Detected",
                    severity = "warning",
                    soldierName = name,
                    soldierSerial = serial,
                    message = "No movement for ${NoMovementSettings.thresholdMinutes.value} min. GPS last known position recorded.",
                    actionRequired = false,
                    notify = notify
                )
            }
        } else {
            firedRules.remove("${soldier.id}_no_movement")
        }
    }
}