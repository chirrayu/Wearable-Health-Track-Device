package com.example.healthmonitor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

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
 * latitude, longitude and status. In production this would be fed by a
 * repository or WebSocket, but for compilation we initialise it empty.
 */
class LiveMapViewModel : ViewModel() {
    // Backing mutable flow
    private val _positions = MutableStateFlow<Map<String, Position>>(emptyMap())
    // Public read‑only flow for UI consumption
    val positions: StateFlow<Map<String, Position>> = _positions

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
