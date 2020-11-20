package com.github.paulpv.helloblescanner.scanners

import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.BuildConfig
import com.github.paulpv.helloblescanner.Utils

abstract class ScannerAbstract(private val applicationContext: Context) {
    companion object {
        private const val TAG = "ScannerAbstract"

        val DEVICE_SCAN_TIMEOUT_SECONDS_DEFAULT = if (BuildConfig.DEBUG) {
            33.0 // 33 seconds
        } else {
            5.5 * 60.0 // 330 seconds == 5.5 minutes
        }
    }

    private val bluetoothAdapter = Utils.getBluetoothAdapter(applicationContext)
    protected val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothLowEnergySupported: Boolean
        get() = Utils.isBluetoothLowEnergySupported(applicationContext)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = Utils.isBluetoothAdapterEnabled(bluetoothAdapter)

    open fun shutdown() {
        Log.i(TAG, "shutdown()")
        scanStop()
    }

    abstract fun scanStart()

    abstract fun scanStop()
}