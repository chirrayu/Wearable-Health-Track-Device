package com.example.healthmonitor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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

    // ⚠ NEW — name -> real backend squad id. Needed because creating a
    // soldier requires a real squad_id, but squads were previously only
    // tracked here by display name with no id at all.
    val squadIds = mutableStateMapOf<String, String>()

    // ⚠ NEW — actually fetches real squads from the backend. Previously
    // `squads` started empty and was only ever appended to locally via
    // the sidebar's "Add Squad" button, which never called the backend
    // either (see addSquad below).
    suspend fun loadSquads() {
        val real = ApiService.getSquads()  // List<Pair<id, name>>
        squadIds.clear()
        squads.clear()
        real.forEach { (id, name) ->
            squadIds[name] = id
            squads.add(name)
        }
    }

    // ⚠ CHANGED — previously just did squads.add(name) locally. Now
    // calls the real backend/squads.py create endpoint and only adds it
    // here once the server confirms and gives back a real id.
    suspend fun addSquad(name: String): Boolean {
        val id = ApiService.createSquad(name) ?: return false
        squadIds[name] = id
        squads.add(name)
        return true
    }
}

object SoldierState {

    // Starts empty — populated from backend on login (via WebSocket
    // snapshots, and now also via refresh() below after any edit).
    val soldiers = mutableStateListOf<Soldier>()

    // ⚠ NEW — refetches the authoritative soldier list straight from the
    // backend. Used after add/update/delete instead of guessing what
    // changed locally — this matters especially for new soldiers, since
    // the real id (a UUID) only exists once the server creates it.
    suspend fun refresh() {
        val real = ApiService.getSoldiers()
        soldiers.clear()
        soldiers.addAll(real)
    }

    // ⚠ CHANGED — previously only did soldiers.add(soldier.copy(id=...))
    // with a locally-generated fake id, and never told the backend at
    // all. Now calls the real backend/soldiers.py create endpoint first,
    // and only reflects it locally once the server confirms — via a
    // fresh refresh() rather than guessing the new soldier's real id.
    suspend fun addSoldier(soldier: Soldier, squadId: String): Boolean {
        val ok = ApiService.createSoldier(soldier, squadId)
        if (ok) refresh()
        return ok
    }

    // ⚠ CHANGED — previously only did soldiers.removeAll { it.id == id }
    // locally. A WebSocket snapshot arriving afterward would silently
    // bring the "removed" soldier right back, since the backend never
    // actually deleted them.
    suspend fun removeSoldier(id: String): Boolean {
        val ok = ApiService.deleteSoldier(id)
        if (ok) refresh()
        return ok
    }

    // ⚠ CHANGED — previously only replaced the entry in the local list.
    // This is the exact bug CasualtyQueueScreen's "Treat"/"Evacuate"
    // buttons were hitting: the soldier would visually leave the queue,
    // then reappear on the next WebSocket snapshot because the backend's
    // copy was never actually updated.
    suspend fun updateSoldier(updated: Soldier): Boolean {
        val ok = ApiService.updateSoldier(updated)
        if (ok) refresh()
        return ok
    }
}