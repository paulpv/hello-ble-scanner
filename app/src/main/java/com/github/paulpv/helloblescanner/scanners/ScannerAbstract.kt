package com.github.paulpv.helloblescanner.scanners

import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.BuildConfig
import com.github.paulpv.helloblescanner.DeviceInfo
import com.github.paulpv.helloblescanner.Utils

abstract class ScannerAbstract(private val applicationContext: Context, private val callbacks: Callbacks) {
    companion object {
        private const val TAG = "ScannerAbstract"

        val DEVICE_SCAN_TIMEOUT_SECONDS_DEFAULT = if (BuildConfig.DEBUG) {
            33.0 // 33 seconds
        } else {
            5.5 * 60.0 // 330 seconds == 5.5 minutes
        }
    }

    interface Callbacks {
        fun onScanningStarted()
        fun onScanningStopped()
        fun onDeviceAdded(deviceInfo: DeviceInfo)
        fun onDeviceUpdated(deviceInfo: DeviceInfo)
        fun onDeviceRemoved(deviceInfo: DeviceInfo)
    }

    protected val bluetoothAdapter = Utils.getBluetoothAdapter(applicationContext)

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

    abstract fun clear()

    abstract fun scanStart()

    abstract fun scanStop()

    protected fun onDeviceAdded(deviceInfo: DeviceInfo) {
        callbacks.onDeviceAdded(deviceInfo)
    }

    protected fun onDeviceUpdated(deviceInfo: DeviceInfo) {
        callbacks.onDeviceUpdated(deviceInfo)
    }

    protected fun onDeviceRemoved(deviceInfo: DeviceInfo) {
        callbacks.onDeviceRemoved(deviceInfo)
    }
}