package com.github.paulpv.helloblescanner

import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.*
import android.util.Log
import com.github.paulpv.helloblescanner.scanners.ScannerAbstract
import com.github.paulpv.helloblescanner.scanners.ScannerNative
import com.github.paulpv.helloblescanner.scanners.ScannerNordic
import com.github.paulpv.helloblescanner.scanners.ScannerSweetBlue
import kotlin.math.ceil

class MyBusinessLogic(private val application: Application, private val looper: Looper) {
    companion object {
        private const val TAG = "MyBusinessLogic"

        private val SCAN_FILTER_EMPTY: ScanFilter = ScanFilter.Builder().build()

        @Suppress("PrivatePropertyName")
        private val PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED = 0L

        val SCANNER_TYPE_DEFAULT = ScannerTypes.Native
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

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            //@formatter:off
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) = this@MyBusinessLogic.onActivityCreated(activity!!)
            override fun onActivityStarted(activity: Activity?) = this@MyBusinessLogic.onActivityStarted(activity!!)
            override fun onActivityResumed(activity: Activity?) = this@MyBusinessLogic.onActivityResumed(activity!!)
            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) = this@MyBusinessLogic.onActivitySaveInstanceState(activity!!, outState)
            override fun onActivityPaused(activity: Activity?) = this@MyBusinessLogic.onActivityPaused(activity!!)
            override fun onActivityStopped(activity: Activity?) = this@MyBusinessLogic.onActivityStopped(activity!!)
            override fun onActivityDestroyed(activity: Activity?) = this@MyBusinessLogic.onActivityDestroyed(activity!!)
            //@formatter:on
        })
    }

    private val observers: MutableSet<ScannerAbstract.Callbacks> = mutableSetOf()

    private var currentActivity: Activity? = null

    @Suppress("MemberVisibilityCanBePrivate")
    val isBackgrounded: Boolean
        get() = currentActivity == null

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    val isForegrounded: Boolean
        get() = !isBackgrounded

    private fun onActivityCreated(activity: Activity) {
        Log.v(TAG, "onActivityCreated(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivityStarted(activity: Activity) {
        Log.v(TAG, "onActivityStarted(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivityResumed(activity: Activity) {
        Log.v(TAG, "onActivityResumed(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        Log.v(TAG, "onActivitySaveInstanceState(activity=$activity, outState=$outState)")
    }

    private fun onActivityPaused(activity: Activity) {
        Log.v(TAG, "onActivityPaused(activity=$activity)")
        activityRemove(activity)
    }

    private fun onActivityStopped(activity: Activity) {
        Log.v(TAG, "onActivityStopped(activity=$activity)")
        activityRemove(activity)
    }

    private fun onActivityDestroyed(activity: Activity) {
        Log.v(TAG, "onActivityDestroyed(activity=$activity)")
        activityRemove(activity)
    }

    private fun activityAdd(activity: Activity) {
        Log.v(TAG, "activityAdd(activity=$activity)")
        if (currentActivity != null) {
            activityRemove(currentActivity!!, false) // false, because we will do it ourself in a few lines...
        }
        currentActivity = activity
        if (activity is ScannerAbstract.Callbacks) {
            attach(activity)
        }
        //scanningNotificationUpdate()
    }

    private fun activityRemove(activity: Activity, updateScanningNotification: Boolean = true) {
        Log.v(TAG, "activityRemove(activity=$activity, updateScanningNotification=$updateScanningNotification)")
        currentActivity = null
        if (activity is ScannerAbstract.Callbacks) {
            detach(activity)
        }
        if (updateScanningNotification) {
            //scanningNotificationUpdate()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun attach(observer: ScannerAbstract.Callbacks) {
        Log.d(TAG, "attach(observer=$observer)")
        synchronized(observers) {
            @Suppress("ControlFlowWithEmptyBody")
            if (observers.add(observer)) {
                //...
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun detach(observer: ScannerAbstract.Callbacks) {
        Log.d(TAG, "detach(observer=$observer)")
        synchronized(observers) {
            @Suppress("ControlFlowWithEmptyBody")
            if (observers.remove(observer)) {
                //...
            }
        }
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
        builder.setReportDelay(0)
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        return builder.build()
    }

    private val nativeScanFilters = newNativeScanFilters()
    private val nativeScanSettings = newNativeScanSettings()

    private val callbacks = object : ScannerAbstract.Callbacks {
        override fun onScanningStarted() = this@MyBusinessLogic.onScanningStarted()
        override fun onScanningStopped() = this@MyBusinessLogic.onScanningStopped()
        override fun onDeviceAdded(deviceInfo: DeviceInfo) = this@MyBusinessLogic.onDeviceAdded(deviceInfo)
        override fun onDeviceUpdated(deviceInfo: DeviceInfo) = this@MyBusinessLogic.onDeviceUpdated(deviceInfo)
        override fun onDeviceRemoved(deviceInfo: DeviceInfo) = this@MyBusinessLogic.onDeviceRemoved(deviceInfo)
    }

    private lateinit var scanner: ScannerAbstract

    enum class ScannerTypes {
        Native,
        Nordic,
        SweetBlue,
    }

    var scannerType: ScannerTypes = SCANNER_TYPE_DEFAULT
        set(value) {
            Log.i(TAG, "scannerType set $value")
            val wasScanning = isScanStarted
            if (wasScanning) {
                scanStop()
            }
            scanner = when (value) {
                ScannerTypes.Native -> ScannerNative(application, callbacks, nativeScanFilters, nativeScanSettings)
                ScannerTypes.Nordic -> ScannerNordic(application, callbacks, nativeScanFilters, nativeScanSettings)
                ScannerTypes.SweetBlue -> ScannerSweetBlue(application, callbacks, nativeScanFilters, nativeScanSettings)
            }
            Log.i(TAG, "scannerType set $scanner")
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

    private fun onScanningStarted() {
        Log.v(TAG, "onScanningStarted()")
    }

    private fun onScanningStopped() {
        Log.v(TAG, "onScanningStopped()")
    }

    private fun onDeviceAdded(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceAdded($deviceInfo)")
        synchronized(observers) {
            observers.forEach { it.onDeviceAdded(deviceInfo) }
        }
    }

    private fun onDeviceUpdated(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceUpdated($deviceInfo)")
        synchronized(observers) {
            observers.forEach { it.onDeviceUpdated(deviceInfo) }
        }
    }

    private fun onDeviceRemoved(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceRemoved($deviceInfo)")
        synchronized(observers) {
            observers.forEach { it.onDeviceRemoved(deviceInfo) }
        }
    }
}