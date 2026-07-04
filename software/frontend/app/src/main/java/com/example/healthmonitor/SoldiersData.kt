/*This file contains the dummy data and original data will also be stored here of soldiers*/
package com.example.healthmonitor

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class Soldier(
    val id: String,
    var name: String,
    var rankTitle: String,
    var rankOrder: Int,
    var serial: String,
    var squad: String,
    var role: String,
    var hr: Int?,
    var spo2: Int?,
    var temp: Float?,
    var battery: Int,
    var status: String,
    var photoUri: String? = null,
    var bloodGroup: String = "O+"
)

fun hrZone(hr: Int?): String {
    if (hr == null) return "none"
    return when {
        hr in 50..100 -> "green"
        hr in 101..130 -> "yellow"
        else -> "red"
    }
}

object SquadState {
    val squads = mutableStateListOf("Alpha", "Bravo", "Charlie", "Delta")
    val selectedSquad = mutableStateOf("All Squads")
}

object SoldierState {

    val soldiers = mutableStateListOf(
        Soldier(
            id = "1", name = "Marcus Webb", rankTitle = "Cpt.", rankOrder = 1,
            serial = "S-001", squad = "Alpha", role = "Squad Leader",
            hr = 72, spo2 = 98, temp = 98.6f, battery = 87, status = "stable",
            bloodGroup = "O+"
        ),
        Soldier(
            id = "2", name = "Rina Patel", rankTitle = "Sgt.", rankOrder = 2,
            serial = "S-002", squad = "Alpha", role = "Medic",
            hr = 68, spo2 = 99, temp = 98.2f, battery = 92, status = "stable",
            bloodGroup = "A+"
        ),
        Soldier(
            id = "3", name = "James Okafor", rankTitle = "Cpl.", rankOrder = 3,
            serial = "S-003", squad = "Bravo", role = "Rifleman",
            hr = 108, spo2 = 93, temp = 100.4f, battery = 61, status = "serious",
            bloodGroup = "B+"
        ),
        Soldier(
            id = "4", name = "Ethan Cruz", rankTitle = "Pvt.", rankOrder = 4,
            serial = "S-004", squad = "Bravo", role = "Rifleman",
            hr = 138, spo2 = 84, temp = 103.1f, battery = 34, status = "critical",
            bloodGroup = "AB-"
        ),
        Soldier(
            id = "5", name = "Yuki Tanaka", rankTitle = "Sgt.", rankOrder = 2,
            serial = "S-005", squad = "Charlie", role = "Scout",
            hr = null, spo2 = null, temp = null, battery = 5, status = "offline",
            bloodGroup = "O-"
        ),
        Soldier(
            id = "6", name = "Sarah Novak", rankTitle = "Lt.", rankOrder = 1,
            serial = "S-006", squad = "Charlie", role = "Sniper",
            hr = 65, spo2 = 98, temp = 98.4f, battery = 79, status = "stable",
            bloodGroup = "A-"
        ),
    )

    private var nextId = 7

    fun addSoldier(soldier: Soldier) {
        soldiers.add(soldier.copy(id = nextId.toString()))
        nextId++
    }

    fun removeSoldier(id: String) {
        soldiers.removeAll { it.id == id }
    }

    fun updateSoldier(updated: Soldier) {
        val index = soldiers.indexOfFirst { it.id == updated.id }
        if (index != -1) soldiers[index] = updated
    }
}