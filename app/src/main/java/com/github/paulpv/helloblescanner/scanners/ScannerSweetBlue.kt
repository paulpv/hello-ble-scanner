package com.github.paulpv.helloblescanner.scanners

import android.content.Context
import android.util.Log
import com.github.paulpv.helloblescanner.DeviceInfo
import com.github.paulpv.helloblescanner.Utils
import com.idevicesinc.sweetblue.*
import com.idevicesinc.sweetblue.utils.Interval


/**
 * TODO: https://sweetblue.io/docs/BleSetupHelper
 */
class ScannerSweetBlue(
    applicationContext: Context,
    callbacks: Callbacks,
    scanFiltersNative: List<android.bluetooth.le.ScanFilter>,
    scanSettingsNative: android.bluetooth.le.ScanSettings
) : ScannerAbstract(applicationContext, callbacks) {
    companion object {
        private val TAG = Utils.TAG(ScannerSweetBlue::class)
    }

    private val recentlyNearbyDevices = mutableMapOf<String, DeviceInfo>()

    private val manager: BleManager
    private val scanFilterSweetBlue: MyScanFilter

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
            //defaultNativeScanFilterList = ...

            //
            // Undiscovery fine-tuning
            //
            cacheDeviceOnUndiscovery = false
            minScanTimeNeededForUndiscovery = Interval.secs(BleManagerConfig.DEFAULT_MINIMUM_SCAN_TIME)
            undiscoverDeviceWhenBleTurnsOff = false
            undiscoveryKeepAlive = Interval.secs(DEVICE_SCAN_TIMEOUT_SECONDS_DEFAULT)
        }

        /**
         * Get a trial key at https://sweetblue.io/#try and provide a package name.
         * Rename app/build.gradle android.defaultConfig.applicationId to the provided package name.
         */
        val apiKey =
            "aF_IbhfP2u35uKXEdcp66yeGNSRLKXPuO1VCX3LCA8YmghcM6IuLvkPaLmAidrlEuLIR90KAWGTJLA_UUI3snn89zyMqfB6Pq1vyOKn866vWbKqVhtNLyeiz5ljS_aYdABEFnxKWVpcM_myYWT8fvq1iBBFW2it7QPkpJC5Cr1fPg98Ako1vXqXoY7OSAxha2_UWd6m3TpNFtx6Bpv3TBFkbN4w-OM9bHx9_iCZl3UA17_zptQuBu4pSX7rhBpzhyTBfIp6vFE_G-eVuXoPl-SMgMdCi9y4h1pIMLOUT1N1aIBgZNc9KMfsCuklq7Umrzca6fxspRkojMRP6fMIg8w"

        manager = BleManager.createInstance(applicationContext, config, apiKey)

        scanFilterSweetBlue = MyScanFilter(scanFiltersNative)
    }

    override fun shutdown() {
        Log.i(TAG, "shutdown()")
        super.shutdown()
        manager.shutdown()
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

        private fun passes(nameNormalized: String, macAddress: String): Boolean {
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

    override fun clear() {
        val it = manager.devices.iterator()
        while (it.hasNext()) {
            val device = it.next()
            manager.removeDeviceFromCache(device)
            onDeviceRemoved(device)
        }
    }

    override fun scanStart() {
        val options = ScanOptions()

        if (!scanFilterSweetBlue.isEmpty()) {
            options.withScanFilter(scanFilterSweetBlue)
        }

        options.withDiscoveryListener { onDiscoveryEvent(it) }

        Log.i(TAG, "scanStart: startScan starting onDiscoveryEvent scan")
        manager.startScan(options)
    }

    override fun scanStop() {
        Log.i(TAG, "scanStop: scanStop stopping onDiscoveryEvent scan")
        manager.stopScan()
    }

    private fun onDiscoveryEvent(discoveryEvent: DiscoveryListener.DiscoveryEvent) {
        //Log.v(TAG, "onDiscoveryEvent: discoveryEvent=$discoveryEvent")
        val device = discoveryEvent.device()
        when (discoveryEvent.lifeCycle()) {
            DiscoveryListener.LifeCycle.DISCOVERED -> onDeviceAdded(device)
            DiscoveryListener.LifeCycle.REDISCOVERED -> onDeviceUpdated(device)
            DiscoveryListener.LifeCycle.UNDISCOVERED -> onDeviceRemoved(device)
            null -> return
        }
    }

    private fun onDeviceAdded(device: BleDevice) {
        Log.v(TAG, "onDeviceAdded($device)")
        val macAddress = device.macAddress
        val deviceInfo = DeviceInfo(macAddress, device.name_normalized, device.rssi)
        recentlyNearbyDevices[macAddress] = deviceInfo
        onDeviceAdded(deviceInfo)
    }

    private fun onDeviceUpdated(device: BleDevice) {
        Log.v(TAG, "onDeviceUpdated($device)")
        val macAddress = device.macAddress
        val deviceInfo = recentlyNearbyDevices[macAddress] ?: return
        deviceInfo.update(device.name_normalized, device.rssi)
        recentlyNearbyDevices[macAddress] = deviceInfo
        onDeviceUpdated(deviceInfo)
    }

    private fun onDeviceRemoved(device: BleDevice) {
        Log.v(TAG, "onDeviceRemoved($device)")
        val macAddress = device.macAddress
        val deviceInfo = recentlyNearbyDevices.remove(macAddress) ?: return
        onDeviceRemoved(deviceInfo)
    }
}