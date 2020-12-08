package com.github.paulpv.helloblescanner.scanners

import android.app.PendingIntent
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.paulpv.helloblescanner.BleScanResult
import com.github.paulpv.helloblescanner.utils.Utils
import com.github.paulpv.helloblescanner.collections.ExpiringIterableLongSparseArray

abstract class ScannerAbstract(
    protected val applicationContext: Context,
    scanResultTimeoutMillis: Long,
    private val callbacks: Callbacks
) {
    companion object {
        private val TAG = Utils.TAG(ScannerAbstract::class)
    }

    interface Callbacks {
        fun onScanResultAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
        fun onScanResultUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
        fun onScanResultRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
    }

    protected val bluetoothAdapter = Utils.getBluetoothAdapter(applicationContext)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothLowEnergySupported: Boolean
        get() = Utils.isBluetoothLowEnergySupported(applicationContext)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = Utils.isBluetoothAdapterEnabled(bluetoothAdapter)

    @Suppress("unused")
    fun bluetoothAdapterEnable(enable: Boolean) = Utils.bluetoothAdapterEnable(bluetoothAdapter, enable)

    @Suppress("unused")
    fun bluetoothAdapterToggle() = Utils.bluetoothAdapterEnable(bluetoothAdapter, !isBluetoothEnabled)

    protected val recentScanResults =
        ExpiringIterableLongSparseArray<BleScanResult>("recentScanResults", scanResultTimeoutMillis)

    val recentScanResultsCount: Int
        get() = recentScanResults.size()

    val recentScanResultsIterator: Iterator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>
        get() = recentScanResults.iterateValues()

    init {
        //@formatter:off
        recentScanResults.addListener(object : ExpiringIterableLongSparseArray.ExpiringIterableLongSparseArrayListener<BleScanResult> {
            override fun onItemAdded(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@ScannerAbstract.onScanResultAdded(item)
            override fun onItemUpdated(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@ScannerAbstract.onScanResultUpdated(item)
            override fun onItemExpiring(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Boolean = this@ScannerAbstract.onScanResultExpiring(item)
            override fun onItemRemoved(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@ScannerAbstract.onScanResultRemoved(item)
        })
        //@formatter:on
    }

    open fun shutdown() {
        Log.i(TAG, "shutdown()")
        scanStop()
    }

    open fun clear() {
        recentScanResults.clear()
    }

    /**
     * Implementor should:
     * 1) Ignore scanPendingIntent if API < 26
     * 2) Save a reference to scanPendingIntent as necessary
     * @param scanPendingIntent null if using ScanCallback, non-null if using PendingIntent
     * @return true if successful; false if not successful
     */
    open fun scanStart(scanFiltersNative: List<ScanFilter>?, scanPendingIntent: PendingIntent?): Boolean {
        Log.i(TAG, "scanStart(scanFiltersNative=$scanFiltersNative, scanPendingIntent=$scanPendingIntent)")
        recentScanResults.resume()
        return true
    }

    /**
     * @return true if successful; false if not successful
     */
    open fun scanStop(): Boolean {
        Log.i(TAG, "scanStop()")
        recentScanResults.pause()
        return true
    }

    @RequiresApi(26)
    abstract fun onScanResultReceived(context: Context, intent: Intent)

    private fun onScanResultAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultAdded($item)")
        callbacks.onScanResultAdded(item)
    }

    private fun onScanResultUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultUpdated($item)")
        callbacks.onScanResultUpdated(item)
    }

    private fun onScanResultExpiring(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Boolean {
        Log.w(TAG, "onScanResultExpiring($item)")
        return false
    }

    private fun onScanResultRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultRemoved($item)")
        callbacks.onScanResultRemoved(item)
    }
}