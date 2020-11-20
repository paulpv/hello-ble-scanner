package com.github.paulpv.helloblescanner.scanners

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.Utils

class ScannerNative(
    applicationContext: Context,
    private val nativeScanFilters: List<ScanFilter>,
    private val nativeScanSettings: ScanSettings
) : ScannerAbstract(applicationContext) {
    companion object {
        private const val TAG = "ScannerNative"
    }

    private val scanningCallback = object : ScanCallback() {
        // @formatter:off
        override fun onScanFailed(errorCode: Int) = this@ScannerNative.onScanFailed("scanningCallback", errorCode)
        override fun onScanResult(callbackType: Int, scanResult: ScanResult?) = this@ScannerNative.onScanResult("scanningCallback", callbackType, scanResult)
        override fun onBatchScanResults(scanResults: MutableList<ScanResult>?) = this@ScannerNative.onBatchScanResults("scanningCallback", scanResults)
        // @formatter:on
    }

    override fun shutdown() {
        Log.i(TAG, "shutdown()")
        super.shutdown()
    }

    override fun scanStart() {
        Log.i(TAG, "scanStart: startScan starting ScanCallback scan")
        bluetoothLeScanner?.startScan(nativeScanFilters, nativeScanSettings, scanningCallback)
    }

    override fun scanStop() {
        Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
        bluetoothLeScanner?.stopScan(scanningCallback)
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        Log.e(TAG, "onScanFailed: caller=$caller, errorCode=$errorCode")
    }

    private fun onBatchScanResults(caller: String, scanResults: MutableList<ScanResult>?) {
        if (scanResults == null) return
        /*
        val msg = "onBatchScanResults($caller, scanResults(${scanResults.size})=..."
        try {
            Log.v(TAG, "+$msg")
        */
        for (scanResult in scanResults) {
            onScanResult("$caller->onBatchScanResults", ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult)
        }
        /*
        } finally {
            Log.v(TAG, "-$msg")
        }
        */
    }

    private fun onScanResult(caller: String, callbackType: Int, scanResult: ScanResult?) {
        // @formatter:off
        Log.v(TAG, "onScanResult: caller=$caller, callbackType=${Utils.callbackTypeToString(callbackType)}, scanResult=$scanResult")
        // @formatter:on
    }
}