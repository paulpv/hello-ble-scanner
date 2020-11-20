package com.github.paulpv.helloblescanner.scanners

import android.content.Context
import com.github.paulpv.helloblescanner.Utils

abstract class ScannerAbstract(private val applicationContext: Context) {
    private val bluetoothAdapter = Utils.getBluetoothAdapter(applicationContext)
    protected val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothLowEnergySupported: Boolean
        get() = Utils.isBluetoothLowEnergySupported(applicationContext)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = Utils.isBluetoothAdapterEnabled(bluetoothAdapter)


    abstract fun scanStart()

    abstract fun scanStop()
}