package com.github.paulpv.helloblescanner.scanners

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.DeviceInfo
import com.github.paulpv.helloblescanner.Utils
import com.github.paulpv.helloblescanner.collections.ExpiringIterableLongSparseArray

class ScannerNative(
    applicationContext: Context,
    callbacks: Callbacks,
    private val scanFiltersNative: List<ScanFilter>,
    private val scanSettingsNative: ScanSettings
) : ScannerAbstract(applicationContext, callbacks) {
    companion object {
        private val TAG = Utils.TAG(ScannerNative::class)
    }

    private val scannerNative = bluetoothAdapter?.bluetoothLeScanner
    private val scanCallbackNative = object : ScanCallback() {
        // @formatter:off
        override fun onScanFailed(errorCode: Int) = this@ScannerNative.onScanFailed("scanningCallback", errorCode)
        override fun onScanResult(callbackType: Int, scanResult: ScanResult?) = this@ScannerNative.onScanResult("scanningCallback", callbackType, scanResult)
        override fun onBatchScanResults(scanResults: List<ScanResult>?) = this@ScannerNative.onBatchScanResults("scanningCallback", scanResults)
        // @formatter:on
    }

    private val recentlyNearbyDevices =
        ExpiringIterableLongSparseArray<DeviceInfo>("recentlyNearbyDevices", DEVICE_SCAN_TIMEOUT_SECONDS_DEFAULT)

    init {
        //@formatter:off
        recentlyNearbyDevices.addListener(object : ExpiringIterableLongSparseArray.ExpiringIterableLongSparseArrayListener<DeviceInfo> {
            override fun onItemAdded(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNative.onDeviceAdded(item)
            override fun onItemUpdated(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNative.onDeviceUpdated(item)
            override fun onItemExpiring(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>): Boolean = this@ScannerNative.onDeviceExpiring(item)
            override fun onItemRemoved(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<DeviceInfo>) = this@ScannerNative.onDeviceRemoved(item)
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
        scannerNative?.startScan(scanFiltersNative, scanSettingsNative, scanCallbackNative)
    }

    override fun scanStop() {
        Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
        recentlyNearbyDevices.pause()
        scannerNative?.stopScan(scanCallbackNative)
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        Log.e(TAG, "onScanFailed: caller=$caller, errorCode=$errorCode")
    }

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
        // @formatter:off
        Log.v(TAG, "onScanResult: caller=$caller, callbackType=${Utils.callbackTypeToString(callbackType)}, scanResult=$scanResult")
        // @formatter:on

        if (scanResult == null) return
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