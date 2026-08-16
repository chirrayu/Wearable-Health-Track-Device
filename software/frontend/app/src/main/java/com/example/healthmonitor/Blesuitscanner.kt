/*Real BLE scanning for suits in BLUETOOTH/fallback mode — replaces the
  hardcoded mock scan list that used to live in PairNewSuitScreen.kt.

  This filters specifically for the service UUID the ESP32 firmware's
  BLEVitalsServer advertises under (see suit_firmware.py's
  _VITALS_SERVICE_UUID) — so only real suits running that firmware show
  up here, not just any nearby Bluetooth device.

  ⚠ HONEST LIMITATION — a BLE advertisement packet only contains a name,
  address, and signal strength (RSSI). It does NOT contain battery level
  or firmware version — those only exist once you actually connect via
  GATT and read the suit's characteristics. This scanner gives you real
  discovery (name/address/signal), not a full connected readout. Wiring
  up a GATT connect-and-read step would be a natural follow-up if you
  want real battery/firmware shown before pairing.*/
package com.example.healthmonitor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.util.UUID

// ⚠ Must match the ESP32 firmware's BLE service UUID exactly — see
// suit_firmware.py's _VITALS_SERVICE_UUID = bluetooth.UUID("6E400001-...").
val SUIT_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

data class DiscoveredSuit(
    val suitName: String,   // from the real BLE advertisement's device name
    val address: String,    // real BLE MAC address — used as the unique id
    val rssi: Int,           // real raw signal strength in dBm
    val signalPercent: Int   // rssi converted to an approximate 0-100% for the UI
)

fun rssiToPercent(rssi: Int): Int {
    // Rough dBm -> percent mapping: -50dBm (excellent, very close) down to
    // -100dBm (unusable, at/beyond range). Not precise, just a readable
    // approximation for the UI — real signal quality varies by environment.
    val clamped = rssi.coerceIn(-100, -50)
    return (((clamped + 100) / 50.0) * 100).toInt().coerceIn(0, 100)
}

object BleSuitScanner {

    private const val TAG = "BleSuitScanner"

    val discovered = mutableStateListOf<DiscoveredSuit>()
    val isScanning = mutableStateOf(false)
    val permissionError = mutableStateOf<String?>(null)

    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        if (isScanning.value) return
        permissionError.value = null

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            permissionError.value = "Bluetooth is off or unavailable on this device"
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            permissionError.value = "BLE scanning unavailable on this device"
            return
        }

        discovered.clear()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SUIT_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = try { device.name ?: "Unknown Suit" } catch (e: SecurityException) { "Unknown Suit" }
                val address = device.address
                val rssi = result.rssi

                val entry = DiscoveredSuit(
                    suitName = name,
                    address = address,
                    rssi = rssi,
                    signalPercent = rssiToPercent(rssi)
                )
                val existingIndex = discovered.indexOfFirst { it.address == address }
                if (existingIndex != -1) {
                    discovered[existingIndex] = entry
                } else {
                    discovered.add(entry)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                permissionError.value = "Scan failed (code $errorCode)"
                isScanning.value = false
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            isScanning.value = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permission: ${e.message}")
            permissionError.value = "Bluetooth permission not granted"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(context: Context) {
        if (!isScanning.value) return
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permission on stop: ${e.message}")
        }
        isScanning.value = false
    }
}