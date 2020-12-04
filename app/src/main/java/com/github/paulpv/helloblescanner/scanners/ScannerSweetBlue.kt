package com.github.paulpv.helloblescanner.scanners

import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.github.paulpv.helloblescanner.BleScanResult
import com.github.paulpv.helloblescanner.utils.Utils
import com.idevicesinc.sweetblue.*
import com.idevicesinc.sweetblue.internal.IBleDevice
import com.idevicesinc.sweetblue.internal.P_BleDeviceImpl
import com.idevicesinc.sweetblue.internal.P_BleManagerImpl
import com.idevicesinc.sweetblue.internal.android.IBluetoothDevice
import com.idevicesinc.sweetblue.utils.Interval
import com.idevicesinc.sweetblue.utils.NativeScanFilter
import com.idevicesinc.sweetblue.utils.Utils_String


/**
 * TODO: https://sweetblue.io/docs/BleSetupHelper
 */
class ScannerSweetBlue(
    applicationContext: Context,
    scanResultTimeoutMillis: Long,
    callbacks: Callbacks,
    scanSettingsNative: android.bluetooth.le.ScanSettings,
    apiKey: String
) : ScannerAbstract(applicationContext, scanResultTimeoutMillis, callbacks) {
    companion object {
        private val TAG = Utils.TAG(ScannerSweetBlue::class)

        /**
         * Inverse of [com.idevicesinc.sweetblue.compat.L_Util.convertNativeFilterList]
         */
        private fun toNativeScanFilters(scanFiltersNative: List<android.bluetooth.le.ScanFilter>): List<NativeScanFilter> {
            val nativeScanFilters = mutableListOf<NativeScanFilter>()
            scanFiltersNative.forEach { scanFilterNative ->
                val builder = NativeScanFilter.Builder()

                if (scanFilterNative.serviceUuidMask != null) {
                    builder.setServiceUuid(scanFilterNative.serviceUuid, scanFilterNative.serviceUuidMask)
                } else if (scanFilterNative.serviceUuid != null) {
                    builder.setServiceUuid(scanFilterNative.serviceUuid)
                }

                if (scanFilterNative.serviceDataMask != null) {
                    builder.setServiceData(
                        scanFilterNative.serviceDataUuid,
                        scanFilterNative.serviceData,
                        scanFilterNative.serviceDataMask
                    )
                } else if (scanFilterNative.serviceData != null) {
                    builder.setServiceData(scanFilterNative.serviceDataUuid, scanFilterNative.serviceData)
                }

                if (scanFilterNative.deviceAddress != null) {
                    builder.setDeviceAddress(scanFilterNative.deviceAddress)
                }

                if (scanFilterNative.deviceName != null) {
                    builder.setDeviceName(scanFilterNative.deviceName)
                }

                if (scanFilterNative.manufacturerDataMask != null) {
                    builder.setManufacturerData(
                        scanFilterNative.manufacturerId,
                        scanFilterNative.manufacturerData,
                        scanFilterNative.manufacturerDataMask
                    )
                } else if (scanFilterNative.manufacturerData != null) {
                    builder.setManufacturerData(scanFilterNative.manufacturerId, scanFilterNative.manufacturerData)
                }

                nativeScanFilters.add(builder.build())

            }
            return nativeScanFilters
        }
    }

    class MyScanFilter(nativeScanFilters: List<android.bluetooth.le.ScanFilter>) : ScanFilter {

        data class NameAddress(val name: String?, val address: String?)

        private val nameAddresses: MutableSet<NameAddress> = mutableSetOf()

        fun isEmpty(): Boolean = nameAddresses.isEmpty()

        init {
            nativeScanFilters.forEach {
                add(NameAddress(it.deviceName, it.deviceAddress))
            }
        }

        fun add(vararg nameAddress: NameAddress) {
            this.nameAddresses.addAll(nameAddress)
        }

        fun passes(device: BleDevice): Boolean {
            return passes(device.name_normalized, device.macAddress)
        }

        fun passes(nameNormalized: String, macAddress: String): Boolean {
            return if (nameAddresses.isEmpty()) {
                true
            } else {
                nameAddresses.forEach {
                    if (it.address == null || it.address.compareTo(macAddress, ignoreCase = true) == 0) {
                        if (it.name == null || it.name.compareTo(nameNormalized, ignoreCase = true) == 0) {
                            return true
                        }
                    }
                }
                false
            }
        }

        override fun onEvent(e: ScanFilter.ScanEvent): ScanFilter.Please {
            return if (passes(e.name_normalized(), e.macAddress())) {
                ScanFilter.Please.acknowledge()
            } else {
                ScanFilter.Please.ignore()
            }
        }
    }

    private val manager: BleManager
    private val discoveryListener: DiscoveryListener

    init {
        val config = BleManagerConfig()
        with(config) {
            //
            // Standard app behavior fine-tuning
            //
            blockingShutdown = true
            enableCrashResolver = true
            //loggingOptions = LogOptions(LogOptions.LogLevel.VERBOSE, LogOptions.LogLevel.VERBOSE)
            scanApi = BleScanApi.AUTO
            scanPower = when (scanSettingsNative.scanMode) {
                BleScanPower.AUTO.nativeMode -> BleScanPower.AUTO // -1 SCAN_MODE_OPPORTUNISTIC
                BleScanPower.VERY_LOW_POWER.nativeMode -> BleScanPower.VERY_LOW_POWER // -1 SCAN_MODE_OPPORTUNISTIC
                BleScanPower.LOW_POWER.nativeMode -> BleScanPower.LOW_POWER // 0 SCAN_MODE_LOW_POWER
                BleScanPower.MEDIUM_POWER.nativeMode -> BleScanPower.MEDIUM_POWER // 1 SCAN_MODE_BALANCED
                BleScanPower.HIGH_POWER.nativeMode -> BleScanPower.HIGH_POWER // 2 SCAN_MODE_LOW_LATENCY
                else -> BleScanPower.AUTO
            }
            autoPauseResumeDetection = false
            stopScanOnPause = false

            // added in 3.2.5
            // https://forum.sweetblue.io/topic/140/sweetblue-3-2-5-release
            // Looking through the code, all this seems to do is request ACCESS_BACKGROUND_LOCATION, which we already do anyway
            // So, I don't this effectively does anything
            requestBackgroundOperation = true // causing a problem?!?!

            //
            // Non-standard fine-tuning
            //
            revertToClassicDiscoveryIfNeeded = false

            //
            // Experimental fine-tuning
            //
            //autoScanDelayAfterBleTurnsOn = Interval.ONE_SEC
            //autoScanDelayAfterResume = Interval.ONE_SEC
            //autoScanPauseTimeWhileAppIsBackgrounded = Interval...
            //defaultListComparator = ...

            /*
            if (nativeScanFilters != null) {
            // Only way to add a filter to PendingIntent scans
            defaultNativeScanFilterList = nativeScanFilters // causing a problem?!?!
            }
            */

            //
            // Undiscovery fine-tuning
            //
            cacheDeviceOnUndiscovery = false
            minScanTimeNeededForUndiscovery = Interval.secs(BleManagerConfig.DEFAULT_MINIMUM_SCAN_TIME)
            undiscoverDeviceWhenBleTurnsOff = false
            undiscoveryKeepAlive = Interval.millis(scanResultTimeoutMillis)
        }

        manager = BleManager.createInstance(applicationContext, config, apiKey)

        discoveryListener = DiscoveryListener { discoveryEvent -> this@ScannerSweetBlue.onDiscoveryEvent(discoveryEvent) }
    }

    override fun shutdown() {
        Log.i(TAG, "shutdown()")
        super.shutdown()
        manager.shutdown()
    }

    override fun clear() {
        super.clear()
        manager.removeAllDevicesFromCache()
    }

    private var scanFilterSweetBlue: MyScanFilter? = null
    private var scanPendingIntent: PendingIntent? = null

    override fun scanStart(
        scanFiltersNative: List<android.bluetooth.le.ScanFilter>?,
        scanPendingIntent: PendingIntent?
    ): Boolean {
        if (!super.scanStart(scanFiltersNative, scanPendingIntent)) {
            return false
        }

        scanFilterSweetBlue = if (scanFiltersNative != null) MyScanFilter(scanFiltersNative) else null

        val config = manager.configClone
        config.defaultNativeScanFilterList =
            if (scanFiltersNative != null) toNativeScanFilters(scanFiltersNative) else BleManagerConfig.EMPTY_NATIVE_FILTER
        manager.setConfig(config)

        @Suppress("NAME_SHADOWING")
        val scanPendingIntent = if (scanPendingIntent == null || Build.VERSION.SDK_INT < 26) null else scanPendingIntent

        val options = ScanOptions()
        if (scanPendingIntent == null) {
            options.withDiscoveryListener(discoveryListener)
            /*
            if (scanFilterSweetBlue != null) {
                options.withScanFilter(scanFilterSweetBlue)
            }
            */
            Log.i(TAG, "scanStart: startScan starting ScanCallback scan")
        } else {
            options.withPendingIntent(scanPendingIntent)
            Log.i(TAG, "scanStart: startScan starting PendingIntent scan")
        }
        // TODO:(pv) Error handling...
        val result = manager.startScan(options)
        if (result) {
            this.scanPendingIntent = scanPendingIntent
        }
        return result
    }

    override fun scanStop(): Boolean {
        if (!super.scanStop()) {
            return false
        }
        // TODO:(pv) Error handling...
        if (scanPendingIntent == null) {
            Log.i(TAG, "scanStop: stopScan stopping ScanCallback scan")
            manager.stopScan()
        } else {
            Log.i(TAG, "scanStart: stopScan stopping PendingIntent scan")
            manager.stopScan(scanPendingIntent)
            this.scanPendingIntent = null
        }
        return true
    }

    private fun scanFilterPass(device: BleDevice): Boolean = scanFilterPass(device.name_normalized, device.macAddress)

    private fun scanFilterPass(name: String, macAddress: String): Boolean {
        val scanFilterSweetBlue = this.scanFilterSweetBlue
        return if (scanFilterSweetBlue == null || scanFilterSweetBlue.passes(name, macAddress)) {
            true
        } else {
            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                Log.w(TAG, "scanFilterPass: filtered out name=$name macAddress=$macAddress; ignoring")
            }
            false
        }
    }

    /**
     * Gets P_BleManagerImpl from BleManager
     */
    @Suppress("FunctionName")
    private fun getP_BleManagerImpl(manager: BleManager): P_BleManagerImpl {
        val privateManagerImpl = BleManager::class.java.getDeclaredField("m_managerImpl")
        privateManagerImpl.isAccessible = true
        return privateManagerImpl.get(manager) as P_BleManagerImpl
    }

    /**
     * Calls [com.idevicesinc.sweetblue.internal.P_BleManagerImpl.newNativeDevice]
     */
    private fun newNativeDevice(manager: P_BleManagerImpl, macAddress: String): IBluetoothDevice {
        val method = P_BleManagerImpl::class.java.getDeclaredMethod(
            "newNativeDevice",
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(manager, macAddress) as IBluetoothDevice
    }

    /**
     * Calls [com.idevicesinc.sweetblue.internal.P_BleManagerImpl.newDevice_private]
     */
    private fun newDevice_private(
        manager: P_BleManagerImpl,
        device_native: IBluetoothDevice,
        name_normalized: String,
        name_native: String,
        origin: BleDeviceOrigin,
        config_nullable: BleDeviceConfig?
    ): IBleDevice {
        val method = P_BleManagerImpl::class.java.getDeclaredMethod(
            "newDevice_private",
            IBluetoothDevice::class.java,
            String::class.java,
            String::class.java,
            BleDeviceOrigin::class.java,
            BleDeviceConfig::class.java
        )
        method.isAccessible = true
        return method.invoke(manager, device_native, name_normalized, name_native, origin, config_nullable) as IBleDevice
    }

    /**
     * Change [com.idevicesinc.sweetblue.internal.P_BleManagerImpl.newDevice] to pass rssi to onDiscovered_wrapItUp
     */
    private fun newDevice(
        manager: P_BleManagerImpl,
        macAddress: String,
        name: String?,
        scanRecord: ByteArray?,
        config: BleDeviceConfig?,
        rssi: Int
    ): IBleDevice {
        @Suppress("LocalVariableName")
        val macAddress_normalized = Utils_String.normalizeMacAddress(macAddress)
        val existingDevice = manager.getDevice(macAddress_normalized)
        if (!existingDevice.isNull) {
            if (config != null) {
                existingDevice.config = config
            }
            if (name != null) {
                existingDevice.setName(name, null, null)
            }
            @Suppress("LocalVariableName")
            val device_native = existingDevice.native
            onDiscovered_wrapItUp(
                manager,
                existingDevice,
                device_native,
                false,
                scanRecord,
                rssi,
                BleDeviceOrigin.EXPLICIT,
                null
            )
            return existingDevice
        } else {
            @Suppress("LocalVariableName")
            val device_native = newNativeDevice(manager, macAddress_normalized)
            @Suppress("LiftReturnOrAssignment")
            if (device_native.isDeviceNull && scanRecord == null) {
                return P_BleDeviceImpl.NULL
            } else {
                @Suppress("LocalVariableName")
                val name_normalized = Utils_String.normalizeDeviceName(name)
                val newDevice =
                    newDevice_private(manager, device_native, name_normalized, name ?: "", BleDeviceOrigin.EXPLICIT, config)
                if (name != null) {
                    newDevice.setName(name, null, null)
                }
                onDiscovered_wrapItUp(manager, newDevice, device_native, true, scanRecord, rssi, BleDeviceOrigin.EXPLICIT, null)
                return newDevice
            }
        }
    }

    /**
     * Calls [com.idevicesinc.sweetblue.internal.P_BleManagerImpl.onDiscovered_wrapItUp]
     */
    private fun onDiscovered_wrapItUp(
        manager: P_BleManagerImpl,
        device: IBleDevice,
        device_native: IBluetoothDevice?,
        newlyDiscovered: Boolean,
        scanRecord_nullable: ByteArray?,
        rssi: Int,
        origin: BleDeviceOrigin,
        scanEvent_nullable: ScanFilter.ScanEvent?
    ) {
        val method = P_BleManagerImpl::class.java.getDeclaredMethod(
            "onDiscovered_wrapItUp",
            IBleDevice::class.java,
            IBluetoothDevice::class.java,
            Boolean::class.java,
            ByteArray::class.java,
            Int::class.java,
            BleDeviceOrigin::class.java,
            ScanFilter.ScanEvent::class.java
        )
        method.isAccessible = true
        method.invoke(manager, device, device_native, newlyDiscovered, scanRecord_nullable, rssi, origin, scanEvent_nullable)
    }

    /**
     * Changes [com.idevicesinc.sweetblue.compat.L_Util.getBleDeviceListFromScanIntent] to add a scan filter.
     */
    @RequiresApi(26)
    private fun getBleDeviceListFromScanIntent(intent: Intent): List<BleDevice> {
        val list = mutableListOf<BleDevice>()
        val bundlelist = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        //Log.e(TAG, "getBleDeviceListFromScanIntent: bundlelist=$bundlelist")
        if (bundlelist != null) {
            val managerImpl = getP_BleManagerImpl(manager)
            val it = bundlelist.iterator()
            while (it.hasNext()) {
                val scanResult = it.next()
                val scanRecord = scanResult.scanRecord?.bytes
                val device = scanResult.device
                val deviceAddress = device.address
                val deviceName = device.name
                val rssi = scanResult.rssi

                @Suppress("LocalVariableName")
                val name_normalized = BleScanResult.getDeviceNameOrScanRecordName(scanResult)

                //
                // Check filter and ignore if no pass
                //
                if (!scanFilterPass(name_normalized, deviceAddress)) {
                    continue
                }

                //Log.e(TAG, "getDevices: #PASS! device=$device")

                val bleDevice = if (false) {
                    // NOTE: Unfortunately this calls onDiscovered_wrapItUp with an rssi == 0
                    manager.newDevice(deviceAddress, deviceName, scanRecord, null)
                } else {
                    val idevice = newDevice(managerImpl, deviceAddress, deviceName, scanRecord, null, rssi)
                    idevice.updateRssi(rssi, true)
                    if (Build.VERSION.SDK_INT >= 26) {
                        idevice.updateKnownTxPower(scanResult.txPower)
                    }
                    P_Bridge_User.newDevice(idevice)
                }
                list.add(bleDevice)
            }
        }
        return list
    }

    @RequiresApi(26)
    override fun onScanResultReceived(context: Context, intent: Intent) {
        val deviceList = if (true) {
            // Ignores scan filter...
            manager.getDevices(intent)
        } else {
            // Does *NOT* ignore scan filter...but uses reflection to do so, so could break...
            getBleDeviceListFromScanIntent(intent)
        }
        deviceList.forEach { bleDevice ->
            onScanResultAddedOrUpdated(bleDevice)
        }
    }

    private fun onDiscoveryEvent(discoveryEvent: DiscoveryListener.DiscoveryEvent) {
        //Log.e(TAG, "onDiscoveryEvent: discoveryEvent=$discoveryEvent")
        val device = discoveryEvent.device()
        if (!scanFilterPass(device)) {
            return
        }
        when (discoveryEvent.lifeCycle()) {
            DiscoveryListener.LifeCycle.DISCOVERED,
            DiscoveryListener.LifeCycle.REDISCOVERED -> onScanResultAddedOrUpdated(device)
            DiscoveryListener.LifeCycle.UNDISCOVERED -> onScanResultRemoved(device)
            null -> return
        }
    }

    private fun onScanResultAddedOrUpdated(device: BleDevice) {
        //Log.e(TAG, "onScanResultAddedOrUpdated($device)")
        if (!scanFilterPass(device)) {
            return
        }
        @Suppress("SimplifyBooleanWithConstants", "ConstantConditionIf")
        if (true) {//false && BuildConfig.DEBUG) {
            Log.v(TAG, "onScanResultAddedOrUpdated: device=$device")
        }
        val macAddressLong = Utils.macAddressStringToLong(device.macAddress)
        var deviceInfo = recentScanResults.get(macAddressLong)
        if (deviceInfo == null) {
            deviceInfo = BleScanResult(device)
        } else {
            deviceInfo.update(device)
        }
        recentScanResults.put(macAddressLong, deviceInfo)
    }

    private fun onScanResultRemoved(device: BleDevice) {
        //Log.e(TAG, "onScanResultRemoved($device)")
        if (!scanFilterPass(device)) {
            return
        }
        @Suppress("SimplifyBooleanWithConstants", "ConstantConditionIf")
        if (true) {//false && BuildConfig.DEBUG) {
            Log.v(TAG, "onScanResultRemoved: device=$device")
        }
        val macAddressLong = Utils.macAddressStringToLong(device.macAddress)
        recentScanResults.remove(macAddressLong)
    }
}