package com.github.paulpv.helloblescanner.scanners

import android.content.Context
import android.os.SystemClock
import com.github.paulpv.helloblescanner.Utils

abstract class ScannerAbstract(private val applicationContext: Context) {
    companion object {
        @Suppress("PrivatePropertyName")
        private val PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED = 0L
    }

    private val bluetoothAdapter = Utils.getBluetoothAdapter(applicationContext)
    protected val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothLowEnergySupported: Boolean
        get() = Utils.isBluetoothLowEnergySupported(applicationContext)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = Utils.isBluetoothAdapterEnabled(bluetoothAdapter)

    var scanStartCount = 0
    private var scanningStartedUptimeMillis = PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED

    val scanningElapsedMillis: Long
        get() {
            val scanningStartedUptimeMillis = this.scanningStartedUptimeMillis
            return if (scanningStartedUptimeMillis != PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED) SystemClock.uptimeMillis() - scanningStartedUptimeMillis else -1L
        }

    open fun scanStart() {
        scanStartCount = 0
        scanningStartedUptimeMillis = SystemClock.uptimeMillis()
    }

    open fun scanStop() {
        //...
    }
}