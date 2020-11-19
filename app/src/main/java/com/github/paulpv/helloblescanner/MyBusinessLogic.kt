package com.github.paulpv.helloblescanner

import android.util.Log

class MyBusinessLogic {
    companion object {
        private const val TAG = "MyBusinessLogic"
    }

    var isScanStarted = false

    enum class ScannerTypes {
        Documented,
        SweetBlue,
        Nordic
    }

    var scannerType = ScannerTypes.Documented

    fun initialize() {
        Log.i(TAG, "initialize")
        //...
    }

    fun scanStart() {
        Log.i(TAG, "scanStart")
        //...
    }

    fun scanStop() {
        Log.i(TAG, "scanStop")
        //...
    }
}