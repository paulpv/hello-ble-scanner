package com.github.paulpv.helloblescanner

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.paulpv.helloblescanner.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.helloblescanner.scanners.ScannerAbstract
import com.github.paulpv.helloblescanner.scanners.ScannerNative
import com.github.paulpv.helloblescanner.scanners.ScannerNordic
import com.github.paulpv.helloblescanner.scanners.ScannerSweetBlue
import kotlin.math.ceil

class MyBusinessLogic(private val application: Application, private val looper: Looper) {
    companion object {
        private val TAG = Utils.TAG(MyBusinessLogic::class)

        fun getInstance(context: Context): MyBusinessLogic? {
            val bleToolApplication = context.applicationContext as? MyApplication ?: return null
            return getInstance(bleToolApplication)
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun getInstance(bleToolApplication: MyApplication): MyBusinessLogic = bleToolApplication.businessLogic

        private const val SCAN_RECEIVER_REQUEST_CODE = 69

        private val SCAN_FILTER_EMPTY: ScanFilter = ScanFilter.Builder().build()

        @Suppress("PrivatePropertyName")
        private val PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED = 0L

        /**
         * Get a trial key at https://sweetblue.io/#try and provide a package name.
         * Rename app/build.gradle android.defaultConfig.applicationId to the provided package name.
         */
        //@formatter:off
        private const val SWEETBLUE_API_KEY = "aF_IbhfP2u35uKXEdcp66yeGNSRLKXPuO1VCX3LCA8YmghcM6IuLvkPaLmAidrlEuLIR90KAWGTJLA_UUI3snn89zyMqfB6Pq1vyOKn866vWbKqVhtNLyeiz5ljS_aYdABEFnxKWVpcM_myYWT8fvq1iBBFW2it7QPkpJC5Cr1fPg98Ako1vXqXoY7OSAxha2_UWd6m3TpNFtx6Bpv3TBFkbN4w-OM9bHx9_iCZl3UA17_zptQuBu4pSX7rhBpzhyTBfIp6vFE_G-eVuXoPl-SMgMdCi9y4h1pIMLOUT1N1aIBgZNc9KMfsCuklq7Umrzca6fxspRkojMRP6fMIg8w"
        //@formatter:on

        val PERMISSIONS = newPermissions()

        private fun newPermissions(): Array<String> {
            val permissions = mutableListOf<String>()
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 29) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            return permissions.toTypedArray()
        }
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

    class BleDeviceScanReceiver : BroadcastReceiver() {
        companion object {
            private val ACTION = "${Utils.getClassName(BleDeviceScanReceiver::class)}.ACTION"

            fun newPendingIntent(context: Context, requestCode: Int, extras: Bundle? = null): PendingIntent? {
                if (Build.VERSION.SDK_INT < 26) return null
                val intent = Intent(context, BleDeviceScanReceiver::class.java)
                intent.action = ACTION
                if (extras != null) {
                    intent.putExtras(extras)
                }
                return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }

        @RequiresApi(26)
        override fun onReceive(context: Context?, intent: Intent?) {
            //Log.e(TAG, "onReceive: context=$context, intent=${Utils.toString(intent)}")
            if (context == null || intent == null) return
            when (intent.action) {
                ACTION -> getInstance(context)?.onScanResultReceived(intent)
            }
        }
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

        if (true) {
            // BEGIN: Bespoke for the purposes of this demo
            scanFilters.add(ScanFilter.Builder().setDeviceName("FNDR").build())
            // END: Bespoke for the purposes of this demo
        }

        if (scanFilters.isEmpty()) {
            scanFilters.add(SCAN_FILTER_EMPTY)
        }
        return scanFilters
    }

    private fun newNativeScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        builder.setReportDelay(0)
        if (Build.VERSION.SDK_INT >= 23) {
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        return builder.build()
    }

    private val nativeScanFilters = newNativeScanFilters()
    private val nativeScanSettings = newNativeScanSettings()

    @Suppress("MemberVisibilityCanBePrivate", "PropertyName")
    val IS_SCAN_PENDING_INTENT_SUPPORTED = Build.VERSION.SDK_INT >= 26

    /**
     * Ignored on systems < API26.
     * On systems >= API26:
     * Set to true to force use of >= API26 [android.app.PendingIntent] scans.
     * Set to false to force use of < API26 [android.bluetooth.le.ScanCallback.ScanCallback] scans.
     */
    @Suppress("MemberVisibilityCanBePrivate", "PropertyName")
    var USE_SCAN_PENDING_INTENT: Boolean = false && IS_SCAN_PENDING_INTENT_SUPPORTED
        set(value) {
            @Suppress("NAME_SHADOWING")
            val value = if (IS_SCAN_PENDING_INTENT_SUPPORTED) value else false
            if (field == value) return
            val wasScanning = isScanStarted
            if (wasScanning) {
                scanStop()
            }
            field = value
            if (wasScanning) {
                scanStart()
            }
        }

    @Suppress("MemberVisibilityCanBePrivate", "PropertyName")
    var USE_SCAN_CALLBACK: Boolean
        get() {
            return !USE_SCAN_PENDING_INTENT
        }
        set(value) {
            USE_SCAN_PENDING_INTENT = !value
        }

    private fun newScanningPendingIntent(): PendingIntent? {
        return BleDeviceScanReceiver.newPendingIntent(application, SCAN_RECEIVER_REQUEST_CODE)
    }

    private val scannerCallbacks = object : ScannerAbstract.Callbacks {
        //@formatter:off
        override fun onScanningStarted() = this@MyBusinessLogic.onScanningStarted()
        override fun onScanningStopped() = this@MyBusinessLogic.onScanningStopped()
        override fun onScanResultAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@MyBusinessLogic.onScanResultAdded(item)
        override fun onScanResultUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@MyBusinessLogic.onScanResultUpdated(item)
        override fun onScanResultRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) = this@MyBusinessLogic.onScanResultRemoved(item)
        //@formatter:on
    }

    /**
     * NEVER CHANGE THE CODES OF EXISTING VALUES!
     */
    enum class ScannerTypes(val code: Int) {
        Native(0),
        Nordic(1),
        SweetBlue(2),
    }

    private val supportsSweetBlue: Boolean
        get() = SWEETBLUE_API_KEY.isNotBlank()

    @Suppress("PrivatePropertyName")
    private val SCANNER_TYPE_DEFAULT = if (supportsSweetBlue) ScannerTypes.SweetBlue else ScannerTypes.Native

    var scannerType: ScannerTypes = SCANNER_TYPE_DEFAULT
        set(value) {
            Log.i(TAG, "scannerType set $value")
            if (value == ScannerTypes.SweetBlue && !supportsSweetBlue) {
                throw IllegalArgumentException("SweetBlue is only supported when SWEETBLUE_API_KEY is not blank")
            }
            if (value == scannerType) return
            val wasScanning = isScanStarted
            if (wasScanning) {
                scanStop()
            }
            _scanner = newScanner(value)
            Log.i(TAG, "scannerType set $scanner")
            field = value
            if (wasScanning) {
                scanStart()
            }
        }

    private var _scanner: ScannerAbstract? = null
    private val scanner: ScannerAbstract
        get() {
            if (_scanner == null) {
                _scanner = newScanner(scannerType)
            }
            return _scanner!!
        }

    private fun newScanner(scannerType: ScannerTypes): ScannerAbstract {
        val scanResultTimeoutMillis = 30 * 1000L
        return when (scannerType) {
            //@formatter:off
            ScannerTypes.Native -> ScannerNative(application, scanResultTimeoutMillis, scannerCallbacks, nativeScanFilters, nativeScanSettings)
            ScannerTypes.Nordic -> ScannerNordic(application, scanResultTimeoutMillis, scannerCallbacks, nativeScanFilters, nativeScanSettings)
            ScannerTypes.SweetBlue -> ScannerSweetBlue(application, scanResultTimeoutMillis, scannerCallbacks, nativeScanFilters, nativeScanSettings, SWEETBLUE_API_KEY)
            //@formatter:on
        }
    }

    init {
        Log.i(TAG, "+init")

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

        Log.i(TAG, "-init")
    }

    fun initialize() {
        Log.i(TAG, "initialize")
        scannerType = SCANNER_TYPE_DEFAULT

        Log.i(TAG, "init: scannerType=$scannerType")
        Log.i(TAG, "init: scanner=$scanner")
        Log.i(TAG, "init: USE_SCAN_PENDING_INTENT=$USE_SCAN_PENDING_INTENT")
    }

    fun clear() {
        Log.i(TAG, "clear()")
        scanner.clear()
    }

    var scanStartCount = 0
    private var scanningStartedUptimeMillis = PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED

    private val scanningElapsedMillis: Long
        get() {
            val scanningStartedUptimeMillis = this.scanningStartedUptimeMillis
            return if (scanningStartedUptimeMillis != PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED) SystemClock.uptimeMillis() - scanningStartedUptimeMillis else -1L
        }

    private var scanningStartedLastScanMillis = PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED
    private var numberOfScanStartsThatSawDevices = 0

    fun scanStart(): Boolean {
        Log.i(TAG, "scanStart")
        if (isScanStarted) return true
        clear()
        isScanStarted = true
        scanStartCount = 0
        scanningStartedUptimeMillis = SystemClock.uptimeMillis()
        val result = scanResume()
        if (!result) {
            scanStop()
        }
        return result
    }

    private fun scanResume(): Boolean {
        Log.i(TAG, "scanResume")
        if (!isScanStarted) return false
        delayedScanningRemoveAll()
        //@formatter:off
        Log.e(TAG, "scanResume: scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis)}, scanStartCount=${scanStartCount}; scanner.scanStart()")
        //@formatter:on
        val scanningPendingIntent = if (USE_SCAN_PENDING_INTENT) newScanningPendingIntent() else null
        scanner.scanStart(scanningPendingIntent)
        scanStartCount++
        delayedScanningPauseAdd()
        return true
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
        scanStartCount = 0
        scanningStartedUptimeMillis = PERSISTENT_SCANNING_STARTED_UPTIME_MILLIS_UNDEFINED
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

    @RequiresApi(26)
    fun onScanResultReceived(intent: Intent) {
        if (!isScanStarted) {
            Log.w(TAG, "onScanResultReceived: isScan == false; ignoring")
            return
        }

        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            Log.e(TAG, "onScanResultReceived(intent=${Utils.toString(intent)})")
        }

        scanner.onScanResultReceived(application, intent)
    }

    private fun onScanResultAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultAdded($item)")
        synchronized(observers) {
            observers.forEach { it.onScanResultAdded(item) }
        }
    }

    private fun onScanResultUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultUpdated($item)")
        synchronized(observers) {
            observers.forEach { it.onScanResultUpdated(item) }
        }
    }

    private fun onScanResultRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultRemoved($item)")
        synchronized(observers) {
            observers.forEach { it.onScanResultRemoved(item) }
        }
    }
}