/*Medical Records — per-soldier medical profile not currently modeled in the
  backend (no medical_records table/router exists in software/backend yet).
  This is a frontend-only in-memory store for now — every soldier starts
  with a genuinely empty record. Data entered here does NOT persist across
  app restarts or sync across devices until a real backend is built for it.
  Shape this to match whatever medical_records.py schema gets added later.*/
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

    // ⚠ CHANGED — previously getOrPut() called seedRecord(), which
    // returned hardcoded fake data (allergies, emergency contacts, an
    // injury log entry) for soldier ids "3" and "4" specifically. Since
    // real soldiers now have backend-generated UUIDs (not simple
    // counters), those ids never matched anything real anyway — this was
    // dead, fake data sitting unused. Every soldier now genuinely starts
    // with a blank record.
    fun getRecord(soldierId: String): MedicalRecord =
        records.getOrPut(soldierId) { MedicalRecord() }
}