package com.github.paulpv.helloblescanner

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.*
import android.util.Log
import com.github.paulpv.helloblescanner.scanners.ScannerAbstract
import com.github.paulpv.helloblescanner.scanners.ScannerNative
import com.github.paulpv.helloblescanner.scanners.ScannerSweetBlue
import kotlin.math.ceil

class MyBusinessLogic(private val applicationContext: Context, private val looper: Looper) {
    companion object {
        private const val TAG = "MyBusinessLogic"

        private val SCAN_FILTER_EMPTY: ScanFilter = ScanFilter.Builder().build()

        @Suppress("PrivatePropertyName")
        private val PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED = 0L

        val SCANNER_TYPE_DEFAULT = ScannerTypes.SweetBlue
    }

    /**
     * TL;DR: As of Android 24 (7/Nougat) [android.bluetooth.le.BluetoothLeScanner.startScan] is limited to 5 calls in 30 seconds.
     * 30 seconds / 5 = average 6 seconds between calls
     * 50% duty cycle = minimum 3 seconds on, 3 seconds off, 3 seconds on, 3 seconds off, repeat...
     * Add 100 milliseconds just to be safe
     *
     * https://blog.classycode.com/undocumented-android-7-ble-behavior-changes-d1a9bd87d983
     * The OS/API will *NOT* generate any errors.
     * You will only see "GattService: App 'yxz' is scanning too frequently" as a logcat error
     *
     * <p>
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/GattService.java#1896
     * <p>
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#286
     * <p>
     * NUM_SCAN_DURATIONS_KEPT = 5
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#82
     * EXCESSIVE_SCANNING_PERIOD_MS = 30 seconds
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#88
     * <p>
     * In summary:
     * "The change prevents an app from stopping and starting BLE scans more than 5 times in a window of 30 seconds.
     * While this sounds like a completely innocuous and sensible change, it still caught us by surprise. One reason we
     * missed it is that the app is not informed of this condition. The scan will start without an error, but the
     * Bluetooth stack will simply withhold advertisements for 30 seconds, instead of informing the app through the
     * error callback ScanCallback.onScanFailed(int)."
     * 5 scans within 30 seconds == 6 seconds per scan.
     * We want 50% duty cycle, so 3 seconds on, 3 seconds off.
     * Increase this number a tiny bit so that we don't get too close to accidentally set off the scan timeout logic.
     * <p>
     * See also:
     * https://github.com/AltBeacon/android-beacon-library/issues/554
     */
    @Suppress("MemberVisibilityCanBePrivate")
    object AndroidBleScanStartLimits {
        //@formatter:off
        const val scanStartLimitCount = 5
        const val scanStartLimitSeconds = 30
        const val scanStartLimitAverageSecondsPerCall = scanStartLimitSeconds / scanStartLimitCount.toFloat()
        const val scanIntervalDutyCycle = 0.5
        val scanStartIntervalAverageMinimumMillis = ceil(scanStartLimitAverageSecondsPerCall * scanIntervalDutyCycle * 1000).toLong()
        val scanStartIntervalAverageSafeMillis = scanStartIntervalAverageMinimumMillis + 100
        //@formatter:on
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
        SweetBlue,
        //Nordic
    }

    var scannerType: ScannerTypes = SCANNER_TYPE_DEFAULT
        set(value) {
            val wasScanning = isScanStarted
            if (wasScanning) {
                scanStop()
            }
            scanner = when (value) {
                ScannerTypes.Native -> ScannerNative(applicationContext, nativeScanFilters, nativeScanSettings)
                ScannerTypes.SweetBlue -> ScannerSweetBlue(applicationContext, nativeScanFilters, nativeScanSettings)
            }
            field = value
            if (wasScanning) {
                scanStart()
            }
        }

    fun initialize() {
        Log.i(TAG, "initialize")
        scannerType = SCANNER_TYPE_DEFAULT
    }

    var scanStartCount = 0
    private var scanningStartedUptimeMillis = PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED

    val scanningElapsedMillis: Long
        get() {
            val scanningStartedUptimeMillis = this.scanningStartedUptimeMillis
            return if (scanningStartedUptimeMillis != PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED) SystemClock.uptimeMillis() - scanningStartedUptimeMillis else -1L
        }

    fun scanStart() {
        Log.i(TAG, "scanStart")
        isScanStarted = true
        scanStartCount = 0
        scanningStartedUptimeMillis = SystemClock.uptimeMillis()
        scanResume()
    }

    private fun scanResume() {
        Log.i(TAG, "scanResume")
        if (!isScanStarted) return
        delayedScanningRemoveAll()
        //@formatter:off
        Log.e(TAG, "scanResume: scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis)}, scanStartCount=${scanStartCount}; scanner.scanStart()")
        //@formatter:on
        scanner.scanStart()
        scanStartCount++
        delayedScanningPauseAdd()
    }

    private fun scanPause() {
        Log.i(TAG, "scanPause")
        delayedScanningRemoveAll()
        //@formatter:off
        Log.e(TAG, "scanPause: scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis)}, scanStartCount=${scanStartCount}; scanner.scanStop()")
        //@formatter:on
        scanner.scanStop()
        if (!isScanStarted) return
        delayedScanningResumeAdd()
    }

    fun scanStop() {
        Log.i(TAG, "scanStop")
        isScanStarted = false
        scanPause()
    }

    private val handler = Handler(this.looper) { msg -> this@MyBusinessLogic.handleMessage(msg) }

    @Suppress("PrivatePropertyName")
    private val MESSAGE_WHAT_PAUSE = 100

    @Suppress("PrivatePropertyName")
    private val MESSAGE_WHAT_RESUME = 101

    private fun handleMessage(msg: Message): Boolean {
        val what = msg.what
        //Log.i(TAG, "handleMessage: msg.what=$what")
        var handled = false
        when (what) {
            MESSAGE_WHAT_PAUSE -> {
                scanPause()
                handled = true
            }
            MESSAGE_WHAT_RESUME -> {
                scanResume()
                handled = true
            }
        }
        return handled
    }

    private fun delayedScanningResumeAdd() {
        Log.v(TAG, "delayedScanningResumeAdd()")
        handler.sendEmptyMessageDelayed(MESSAGE_WHAT_RESUME, AndroidBleScanStartLimits.scanStartIntervalAverageSafeMillis)
    }

    private fun delayedScanningResumeRemove() {
        Log.v(TAG, "delayedScanningResumeRemove()")
        handler.removeMessages(MESSAGE_WHAT_RESUME)
    }

    private fun delayedScanningPauseAdd() {
        Log.v(TAG, "delayedScanningPauseAdd()")
        handler.sendEmptyMessageDelayed(MESSAGE_WHAT_PAUSE, AndroidBleScanStartLimits.scanStartIntervalAverageSafeMillis)
    }

    private fun delayedScanningPauseRemove() {
        Log.v(TAG, "delayedScanningPauseRemove()")
        handler.removeMessages(MESSAGE_WHAT_PAUSE)
    }

    private fun delayedScanningRemoveAll() {
        Log.v(TAG, "delayedScanningRemoveAll()")
        delayedScanningResumeRemove()
        delayedScanningPauseRemove()
    }
}