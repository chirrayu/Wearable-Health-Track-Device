package com.example.healthmonitor

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
        hr in 50..100  -> "green"
        hr in 101..130 -> "yellow"
        else           -> "red"
    }
}

object SquadState {
    val squads        = mutableStateListOf<String>()
    val selectedSquad = mutableStateOf("All Squads")
}

object SoldierState {

    // Starts empty — populated from backend on login
    val soldiers = mutableStateListOf<Soldier>()

    private var nextId = 1

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