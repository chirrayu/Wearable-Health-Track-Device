package com.example.healthmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

// Simple data class representing a position on the map
data class Position(
    val id: String,
    val lat: Double,
    val lng: Double,
    val status: String
)

/**
 * ViewModel for the LiveMapScreen.
 *
 * Exposes a StateFlow of a map keyed by soldier id to a Position containing
 * latitude, longitude and status. The flow is updated from WebSocket messages
 * via LiveMapState.pendingMapUpdate.
 */
class LiveMapViewModel : ViewModel() {
    // Backing mutable flow
    private val _positions = MutableStateFlow<Map<String, Position>>(emptyMap())
    // Public read‑only flow for UI consumption
    val positions: StateFlow<Map<String, Position>> = _positions

    init {
        // Observe pending map updates emitted by WebSocketManager
        viewModelScope.launch {
            snapshotFlow { LiveMapState.pendingMapUpdate.value }
                .filterNotNull()
                .collectLatest { mapUpdate ->
                    updatePosition(mapUpdate.soldierId, mapUpdate.lat, mapUpdate.lng, mapUpdate.status)
                    // Reset the pending update to avoid reprocessing
                    LiveMapState.pendingMapUpdate.value = null
                }
        }
    }

    /**
     * Update or add a position entry.
     */
    fun updatePosition(id: String, lat: Double, lng: Double, status: String) {
        _positions.update { current ->
            current.toMutableMap().apply {
                this[id] = Position(id, lat, lng, status)
            }
        }
    }
}
