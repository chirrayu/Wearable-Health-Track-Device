/*Medical Records — per-soldier medical profile not currently modeled in the
  backend (no medical_records table/router exists in software/backend yet).
  This is a frontend-only in-memory store for now; shape it to match
  whatever medical_records.py schema gets added later.*/
package com.example.healthmonitor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

data class InjuryLogEntry(
    val timestamp: String,
    val description: String,
    val treatment: String
)

data class MedicalRecord(
    val allergies: MutableList<String> = mutableStateListOf(),
    val conditions: MutableList<String> = mutableStateListOf(),
    var emergencyContactName: String = "",
    var emergencyContactRelation: String = "",
    var emergencyContactPhone: String = "",
    var notes: String = "",
    val injuryLog: MutableList<InjuryLogEntry> = mutableStateListOf()
)

object MedicalRecordState {

    val records = mutableStateMapOf<String, MedicalRecord>()

    fun getRecord(soldierId: String): MedicalRecord =
        records.getOrPut(soldierId) { seedRecord(soldierId) }

    // A few realistic starting records so the screen doesn't look empty on
    // first load — mirrors the way SoldierState ships with sample soldiers.
    private fun seedRecord(soldierId: String): MedicalRecord = when (soldierId) {
        "3" -> MedicalRecord(
            allergies = mutableStateListOf("Penicillin"),
            conditions = mutableStateListOf("Mild asthma"),
            emergencyContactName = "Ngozi Okafor",
            emergencyContactRelation = "Spouse",
            emergencyContactPhone = "+1 555-0143",
            notes = "Carries personal inhaler in suit pouch.",
            injuryLog = mutableStateListOf(
                InjuryLogEntry("2026-06-02", "Ankle sprain, training exercise", "RICE protocol, 3-day rest")
            )
        )
        "4" -> MedicalRecord(
            allergies = mutableStateListOf("None known"),
            conditions = mutableStateListOf(),
            emergencyContactName = "Maria Cruz",
            emergencyContactRelation = "Mother",
            emergencyContactPhone = "+1 555-0198",
            notes = "",
            injuryLog = mutableStateListOf()
        )
        else -> MedicalRecord()
    }
}
