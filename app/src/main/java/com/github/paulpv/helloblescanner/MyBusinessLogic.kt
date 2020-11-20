package com.github.paulpv.helloblescanner

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.github.paulpv.helloblescanner.scanners.ScannerAbstract
import com.github.paulpv.helloblescanner.scanners.ScannerNative
import com.github.paulpv.helloblescanner.scanners.ScannerSweetBlue

class MyBusinessLogic(private val applicationContext: Context) {
    companion object {
        private const val TAG = "MyBusinessLogic"

        val SCAN_FILTER_EMPTY: ScanFilter = ScanFilter.Builder().build()
    }

    var isScanStarted = false
        private set(value) {
            field = value
        }

    private fun newNativeScanFilters(): List<ScanFilter> {
        val scanFilters = mutableListOf<ScanFilter>()

        // BEGIN: Bespoke for the purposes of this demo
        scanFilters.add(ScanFilter.Builder().setDeviceName("FNDR").build())
        // END: Bespoke for the purposes of this demo

        if (scanFilters.isEmpty()) {
            scanFilters.add(SCAN_FILTER_EMPTY)
        }
        return scanFilters
    }

    private fun newNativeScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        @SuppressLint("ObsoleteSdkInt")
        if (Build.VERSION.SDK_INT >= 23) {
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        return builder.build()
    }

    private val nativeScanFilters = newNativeScanFilters()
    private val nativeScanSettings = newNativeScanSettings()

    private lateinit var scanner: ScannerAbstract

    enum class ScannerTypes {
        Native,
        //SweetBlue,
        //Nordic
    }

    var scannerType: ScannerTypes = ScannerTypes.Native
        set(value) {
            if (isScanStarted) {
                throw IllegalStateException("cannot set scannerType while scanning")
            }
            scanner = when (value) {
                ScannerTypes.Native -> {
                    ScannerNative(applicationContext, nativeScanFilters, nativeScanSettings)
                }
            }
            field = value
        }

    fun initialize() {
        Log.i(TAG, "initialize")
        scannerType = ScannerTypes.Native
    }

    fun scanStart() {
        Log.i(TAG, "scanStart")
        scanner.scanStart()
        isScanStarted = true
    }

    fun scanStop() {
        Log.i(TAG, "scanStop")
        scanner.scanStop()
        isScanStarted = false
    }
}