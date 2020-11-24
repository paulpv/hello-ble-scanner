package com.github.paulpv.helloblescanner.scanners

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.DeviceInfo
import com.github.paulpv.helloblescanner.Utils
import com.github.paulpv.helloblescanner.collections.ExpiringIterableLongSparseArray
import no.nordicsemi.android.support.v18.scanner.*
import java.util.*

class ScannerNordic(
    applicationContext: Context,
    callbacks: Callbacks,
    scanFiltersNative: List<android.bluetooth.le.ScanFilter>,
    scanSettingsNative: android.bluetooth.le.ScanSettings
) : ScannerAbstract(applicationContext, callbacks) {
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
    private val scanFiltersNordic = scanFiltersNativeToNordic(scanFiltersNative)
    private val scanSettingsNordic = scanSettingsNativeToNordic(scanSettingsNative)
    private val scanCallbackNordic = object : ScanCallback() {
        //@formatter:off
        override fun onScanResult(callbackType: Int, result: ScanResult) = this@ScannerNordic.onScanResult("ScanCallback", callbackType, result)
        override fun onBatchScanResults(results: List<ScanResult>) = this@ScannerNordic.onBatchScanResults("ScanCallback", results)
        override fun onScanFailed(errorCode: Int) = this@ScannerNordic.onScanFailed("ScanCallback", errorCode)
        //@formatter:on
    }

    private val recentlyNearbyDevices =
        ExpiringIterableLongSparseArray<DeviceInfo>("recentlyNearbyDevices", DEVICE_SCAN_TIMEOUT_SECONDS_DEFAULT)

    init {
        //@formatter:off
        recentlyNearbyDevices.addListener(object : ExpiringIterableLongSparseArray.ExpiringIterableLongSparseArrayListener<DeviceInfo> {
            override fun onItemAdded(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNordic.onDeviceAdded(item)
            override fun onItemUpdated(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNordic.onDeviceUpdated(item)
            override fun onItemExpiring(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>): Boolean = this@ScannerNordic.onDeviceExpiring(item)
            override fun onItemRemoved(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNordic.onDeviceRemoved(item)
        })
        //@formatter:on
    }

    override fun shutdown() {
        Log.i(TAG, "shutdown()")
        super.shutdown()
    }

    override fun clear() {
        recentlyNearbyDevices.clear()
    }

    override fun scanStart() {
        Log.i(TAG, "scanStart: startScan starting ScanCallback scan")
        scannerNordic.startScan(scanFiltersNordic, scanSettingsNordic, scanCallbackNordic)
    }

    override fun scanStop() {
        Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
        recentlyNearbyDevices.pause()
        scannerNordic.stopScan(scanCallbackNordic)
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        Log.e(TAG, "onScanFailed: caller=$caller, errorCode=$errorCode")
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
        // @formatter:off
        Log.v(TAG, "onScanResult: caller=$caller, callbackType=${Utils.callbackTypeToString(callbackType)}, scanResult=$scanResult")
        // @formatter:on

        val device = scanResult.device
        val macAddressString = device.address
        val macAddressLong = Utils.macAddressStringToLong(macAddressString)
        var deviceInfo = recentlyNearbyDevices.get(macAddressLong)
        if (deviceInfo == null) {
            deviceInfo = DeviceInfo(scanResult.device.address, DeviceInfo.getDeviceName(scanResult), scanResult.rssi)
        } else {
            deviceInfo.update(DeviceInfo.getDeviceName(scanResult), scanResult.rssi)
        }
        recentlyNearbyDevices.put(macAddressLong, deviceInfo)
    }

    private fun onDeviceAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) {
        Log.v(TAG, "onDeviceAdded($item)")
        onDeviceAdded(item.value)
    }

    private fun onDeviceUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) {
        Log.v(TAG, "onDeviceUpdated($item)")
        onDeviceUpdated(item.value)
    }

    private fun onDeviceExpiring(item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>): Boolean {
        Log.w(TAG, "onDeviceExpiring($item)")
        /*
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bleDevice = scanResult.device
        val macAddressString = bleDevice.address
        val timeoutMillis = item.timeoutMillis
        // @formatter:off
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: timeoutMillis=$timeoutMillis")
        //Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: EXPIRING...")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: isPersistentScanningEnabled=$isPersistentScanningEnabled")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: persistentScanningElapsedMillis=$persistentScanningElapsedMillis")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: isBluetoothEnabled=$isBluetoothEnabled")
        @Suppress("UnnecessaryVariable","SimplifyBooleanWithConstants")
        val keep = !isPersistentScanningEnabled || persistentScanningElapsedMillis < DEVICE_SCAN_TIMEOUT_MILLIS || !isBluetoothEnabled || (false && BuildConfig.DEBUG)
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: keep=$keep")
        // @formatter:on
        return keep
        */
        return false
    }

    private fun onDeviceRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) {
        Log.v(TAG, "onDeviceRemoved($item)")
        onDeviceRemoved(item.value)
    }
}