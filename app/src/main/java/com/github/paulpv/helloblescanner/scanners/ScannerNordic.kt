package com.github.paulpv.helloblescanner.scanners

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.paulpv.helloblescanner.BleScanResult
import com.github.paulpv.helloblescanner.utils.Utils
import no.nordicsemi.android.support.v18.scanner.*
import java.util.*

class ScannerNordic(
    applicationContext: Context,
    scanResultTimeoutMillis: Long,
    callbacks: Callbacks,
    scanSettingsNative: android.bluetooth.le.ScanSettings
) : ScannerAbstract(applicationContext, scanResultTimeoutMillis, callbacks) {
    companion object {
        private val TAG = Utils.TAG(ScannerNordic::class)

        fun scanFiltersNativeToNordic(scanFiltersNative: List<android.bluetooth.le.ScanFilter>?): List<ScanFilter> {
            val scanFiltersNordic = mutableListOf<ScanFilter>()
            if (scanFiltersNative != null) {
                for (scanFilterNative in scanFiltersNative) {
                    val builder = ScanFilter.Builder()

                    val deviceAddress = scanFilterNative.deviceAddress
                    if (deviceAddress == null || BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                        builder.setDeviceAddress(deviceAddress)
                    }

                    builder.setDeviceName(scanFilterNative.deviceName)

                    val manufacturerId = scanFilterNative.manufacturerId
                    val manufacturerData = scanFilterNative.manufacturerData
                    val manufacturerDataMask = scanFilterNative.manufacturerDataMask
                    builder.setManufacturerData(manufacturerId, manufacturerData, manufacturerDataMask)

                    val serviceDataUuid = scanFilterNative.serviceDataUuid
                    val serviceData = scanFilterNative.serviceData
                    val serviceDataMask = scanFilterNative.serviceDataMask
                    if (serviceDataUuid != null) {
                        builder.setServiceData(serviceDataUuid, serviceData, serviceDataMask)
                    }

                    val serviceUuid = scanFilterNative.serviceUuid
                    val serviceUuidMask = scanFilterNative.serviceUuidMask
                    builder.setServiceUuid(serviceUuid, serviceUuidMask)

                    val scanFilter = builder.build()

                    scanFiltersNordic.add(scanFilter)
                }
            }
            return scanFiltersNordic
        }

        fun scanSettingsNativeToNordic(scanSettingsNative: android.bluetooth.le.ScanSettings): ScanSettings {
            val builder = ScanSettings.Builder()
            // NOTE: These settings are **NOT** guaranteed to match 1 to 1!!!
            // Hand written for now to match scanSettingsNative...
            builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)//scanSettingsNative.scanMode)
            builder.setReportDelay(0)//scanSettingsNative.reportDelayMillis)
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)//scanSettingsNative.callbackType)
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)// no scanSettingsNative getter?!?!?!
            builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)// no scanSettingsNative getter?!?!?!
            return builder.build()
        }

        /*
        fun scanRecordNordicToNative(scanRecord: ScanRecord?): android.bluetooth.le.ScanRecord? {
            return if (scanRecord == null) null else scanRecordFromBytes(scanRecord.bytes)
        }

        /**
         * public static ScanRecord parseFromBytes(byte[] scanRecord) is annotated with @hide.
         * To call this methods I see two options:
         * 1) use reflection to call parseFromBytes
         * 2) copy the code
         * 3) don't call parseFromBytes, but use reflection to call ScanRecord constructor
         * For now I am choosing the former.
         * If that stops working I will choose the latter.
         *
         * @param scanRecordBytes
         * @return
         */
        fun scanRecordFromBytes(scanRecordBytes: ByteArray?): android.bluetooth.le.ScanRecord? {
            if (scanRecordBytes == null) {
                return null
            }
            val method = try {
                android.bluetooth.le.ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
            } catch (e: NoSuchMethodException) {
                return null
            }
            method.isAccessible = true
            return try {
                method.invoke(null, *scanRecordBytes) as android.bluetooth.le.ScanRecord
            } catch (e: IllegalAccessException) {
                return null
            } catch (e: InvocationTargetException) {
                return null
            }
        }
        */
    }

    private val scannerNordic = BluetoothLeScannerCompat.getScanner()
    private val scanSettingsNordic = scanSettingsNativeToNordic(scanSettingsNative)
    private val scanCallbackNordic = object : ScanCallback() {
        //@formatter:off
        override fun onScanResult(callbackType: Int, result: ScanResult) = this@ScannerNordic.onScanResult("ScanCallback", callbackType, result)
        override fun onBatchScanResults(results: List<ScanResult>) = this@ScannerNordic.onBatchScanResults("ScanCallback", results)
        override fun onScanFailed(errorCode: Int) = this@ScannerNordic.onScanFailed("ScanCallback", errorCode)
        //@formatter:on
    }

    private var scanPendingIntent: PendingIntent? = null

    override fun scanStart(scanFiltersNative: List<android.bluetooth.le.ScanFilter>?, scanPendingIntent: PendingIntent?): Boolean {
        if (!super.scanStart(scanFiltersNative, scanPendingIntent)) {
            return false
        }
        val scanFiltersNordic = scanFiltersNativeToNordic(scanFiltersNative)
        // TODO:(pv) Error handling...
        return if (scanPendingIntent == null || Build.VERSION.SDK_INT < 26) {
            Log.i(TAG, "scanStart: startScan starting ScanCallback scan")
            scannerNordic.startScan(scanFiltersNordic, scanSettingsNordic, scanCallbackNordic)
            true
        } else {
            Log.i(TAG, "scanStart: startScan starting PendingIntent scan")
            scannerNordic.startScan(scanFiltersNordic, scanSettingsNordic, applicationContext, scanPendingIntent)
            true
        }
        return true
    }

    override fun scanStop(): Boolean {
        if (!super.scanStop()) {
            return false
        }
        // TODO:(pv) Error handling...
        val scanPendingIntent = this.scanPendingIntent
        return if (scanPendingIntent == null || Build.VERSION.SDK_INT < 26) {
            Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
            scannerNordic.stopScan(scanCallbackNordic)
            true
        } else {
            Log.i(TAG, "scanStart: stopScan stopping PendingIntent scan")
            scannerNordic.stopScan(applicationContext, scanPendingIntent)
            this.scanPendingIntent = null
            true
        }
    }

    @RequiresApi(26)
    override fun onScanResultReceived(context: Context, intent: Intent) {
        //......
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        Log.e(TAG, "onScanFailed: caller=$caller, errorCode=$errorCode")
        // TODO:(pv) Error handling...
    }

    private fun onBatchScanResults(caller: String, scanResults: List<ScanResult>) {
        /*
        val msg = "onBatchScanResults($caller, scanResults(${scanResults.size})=..."
        try {
            Log.v(TAG, "+$msg")
        */
        for (scanResult in scanResults) {
            onScanResult("$caller->onBatchScanResults", android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult)
        }
        /*
        } finally {
            Log.v(TAG, "-$msg")
        }
        */
    }

    private fun onScanResult(caller: String, callbackType: Int, scanResult: ScanResult) {
        val device = scanResult.device
        val macAddressString = device.address

        @Suppress("SimplifyBooleanWithConstants", "ConstantConditionIf")
        if (true) {//false && BuildConfig.DEBUG) {
            Log.v(
                TAG,
                "onScanResult: caller=$caller, callbackType=${Utils.callbackTypeToString(callbackType)}, scanResult=$scanResult"
            )
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