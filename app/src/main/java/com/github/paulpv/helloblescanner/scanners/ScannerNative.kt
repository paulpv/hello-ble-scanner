package com.github.paulpv.helloblescanner.scanners

import android.app.PendingIntent
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.paulpv.helloblescanner.BleScanResult
import com.github.paulpv.helloblescanner.utils.Utils

class ScannerNative(
    applicationContext: Context,
    scanResultTimeoutMillis: Long,
    callbacks: Callbacks,
    private val scanSettingsNative: ScanSettings
) : ScannerAbstract(applicationContext, scanResultTimeoutMillis, callbacks) {
    companion object {
        private val TAG = Utils.TAG(ScannerNative::class)
    }

    private val scanCallbackNative = object : ScanCallback() {
        // @formatter:off
        override fun onScanFailed(errorCode: Int) = this@ScannerNative.onScanFailed("scanningCallback", errorCode)
        override fun onScanResult(callbackType: Int, scanResult: ScanResult?) = this@ScannerNative.onScanResult("scanningCallback", callbackType, scanResult)
        override fun onBatchScanResults(scanResults: List<ScanResult>?) = this@ScannerNative.onBatchScanResults("scanningCallback", scanResults)
        // @formatter:on
    }

    private var scanPendingIntent: PendingIntent? = null

    override fun scanStart(scanFiltersNative: List<ScanFilter>?, scanPendingIntent: PendingIntent?): Boolean {
        if (!super.scanStart(scanFiltersNative, scanPendingIntent)) {
            return false
        }
        val scannerNative = bluetoothAdapter?.bluetoothLeScanner
        if (scannerNative == null) {
            Log.w(TAG, "scanStart: scannerNative == null; ignoring")
            return false
        }
        // TODO:(pv) Error handling...
        return if (scanPendingIntent == null || Build.VERSION.SDK_INT < 26) {
            Log.i(TAG, "scanStart: startScan starting ScanCallback scan")
            scannerNative.startScan(scanFiltersNative, scanSettingsNative, scanCallbackNative)
            true
        } else {
            Log.i(TAG, "scanStart: startScan starting PendingIntent scan")
            val errorCode = scannerNative.startScan(scanFiltersNative, scanSettingsNative, scanPendingIntent)
            this.scanPendingIntent = scanPendingIntent
            errorCode == 0
        }
    }

    override fun scanStop(): Boolean {
        if (!super.scanStop()) {
            return false
        }
        val scannerNative = bluetoothAdapter?.bluetoothLeScanner
        if (scannerNative == null) {
            Log.w(TAG, "scanStop: scannerNative == null; ignoring")
            return false
        }
        // TODO:(pv) Error handling...
        return if (scanPendingIntent == null || Build.VERSION.SDK_INT < 26) {
            Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
            scannerNative.stopScan(scanCallbackNative)
            true
        } else {
            Log.i(TAG, "scanStart: stopScan stopping PendingIntent scan")
            scannerNative.stopScan(scanPendingIntent)
            this.scanPendingIntent = null
            true
        }
    }

    @RequiresApi(26)
    override fun onScanResultReceived(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (errorCode != 0) {
            onScanFailed("PendingIntent", errorCode)
            return
        }

        val scanResults = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        @Suppress("FoldInitializerAndIfToElvis")
        if (scanResults == null) {
            return
        }

        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        if (callbackType == -1) {
            onBatchScanResults("PendingIntent", scanResults)
        } else {
            for (scanResult in scanResults) {
                onScanResult("PendingIntent", callbackType, scanResult)
            }
        }
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        Log.e(TAG, "onScanFailed: caller=$caller, errorCode=$errorCode")
        // TODO:(pv) Error handling...
        //onScanFailed(caller, BleScanException(errorCode))
    }

    /*
    private fun onScanFailed(caller: String, error: Throwable) {
        Log.e(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} onScanFailed: $caller $error")
        persistentScanningStop(error)
    }
    */

    private fun onBatchScanResults(caller: String, scanResults: List<ScanResult>?) {
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
        if (scanResult == null) return

        val device = scanResult.device
        val macAddressString = device.address

        @Suppress("SimplifyBooleanWithConstants", "ConstantConditionIf")
        if (true) {//false && BuildConfig.DEBUG) {
            //@formatter:off
            Log.v(TAG, "onScanResult: caller=$caller, callbackType=${Utils.callbackTypeToString(callbackType)}, scanResult=$scanResult")
            //@formatter:on
        }

        val macAddressLong = Utils.macAddressStringToLong(macAddressString)
        var deviceInfo = recentScanResults.get(macAddressLong)
        if (deviceInfo == null) {
            deviceInfo = BleScanResult(scanResult)
        } else {
            deviceInfo.update(scanResult)
        }
        recentScanResults.put(macAddressLong, deviceInfo)
    }
}