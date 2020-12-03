package com.github.paulpv.helloblescanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import com.github.paulpv.helloblescanner.utils.LowPassFilter
import java.lang.reflect.InvocationTargetException

class BleScanResult {
    companion object {
        private val TAG = Utils.TAG(BleScanResult::class)

        //private val deviceInfoPool = ArrayQueue<DeviceInfo>("DeviceInfoPool")

        /**
         * NOTE:(pv) It is possible for device.name to return null even though the scan says otherwise
         * @return scanResult.device.name or scanResult.scanRecord.deviceName or device.address
         */
        fun getDeviceNameOrScanRecordName(scanResult: android.bluetooth.le.ScanResult): String {
            val device = scanResult.device
            var deviceName: String? = device.name
            if (deviceName != null) {
                return deviceName
            }
            //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.device.name == null")
            val scanRecord = scanResult.scanRecord
            if (scanRecord != null) {
                deviceName = scanRecord.deviceName
                if (deviceName != null) {
                    return deviceName
                }
                //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord.deviceName == null")
            } else {
                //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord == null")
            }
            deviceName = device.address
            return deviceName
        }

        /**
         * NOTE:(pv) It is possible for device.name to return null even though the scan says otherwise
         * @return scanResult.device.name or scanResult.scanRecord.deviceName or device.address
         */
        fun getDeviceNameOrScanRecordName(scanResult: no.nordicsemi.android.support.v18.scanner.ScanResult): String {
            val device = scanResult.device
            var deviceName: String? = device.name
            if (deviceName != null) {
                return deviceName
            }
            //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.device.name == null")
            val scanRecord = scanResult.scanRecord
            if (scanRecord != null) {
                deviceName = scanRecord.deviceName
                if (deviceName != null) {
                    return deviceName
                }
                //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord.deviceName == null")
            } else {
                //Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord == null")
            }
            deviceName = device.address
            return deviceName
        }

        fun scanRecordFromBytes(scanResult: no.nordicsemi.android.support.v18.scanner.ScanResult): ScanRecord? {
            return scanRecordFromBytes(scanResult.scanRecord)
        }

        fun scanRecordFromBytes(scanRecord: no.nordicsemi.android.support.v18.scanner.ScanRecord?): ScanRecord? {
            return scanRecordFromBytes(scanRecord?.bytes)
        }

        /**
         * public static ScanRecord parseFromBytes(byte[] scanRecord) is annotated with @hide.
         * To call this methods I see two options:
         * 1) use reflection to call parseFromBytes
         * 2) copy the non-trivial code
         * For now I am choosing option #1.
         * If that stops working I will choose option #2.
         *
         * @param scanRecordBytes
         * @return ScanRecord?
         */
        fun scanRecordFromBytes(scanRecordBytes: ByteArray?): ScanRecord? {
            if (scanRecordBytes == null) {
                return null
            }
            // TODO:(pv) Use Kotlin KClass reflection instead of Java Class reflection?
            val method = try {
                ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
            } catch (e: NoSuchMethodException) {
                return null
            }
            method.isAccessible = true
            return try {
                method.invoke(null, scanRecordBytes) as ScanRecord
            } catch (e: IllegalAccessException) {
                return null
            } catch (e: InvocationTargetException) {
                return null
            }
        }

        /*
        fun newInstance(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>, bleDevice: BleDevice?): DeviceInfo {

            // TODO:(pv) Pull these from a pool of unused items to mitigate trashing memory so much
            //  This requires identifying in the code a place where the item will no longer be used

            val bleScanResult = item.value
            val scanResult = bleScanResult.scanResult
            val device = scanResult.device

            var address = device.address
            var name = getDeviceName(scanResult)
            var modelNumber = Pebblebee.DeviceModelNumber.getDeviceModelNumber(bleDevice)
            var rssi = bleScanResult.rssi
            var rssiSmoothed = bleScanResult.rssiSmoothed
            var isClicked = BleDeviceFeatures.isClicked(bleDevice)
            var batteryPercent = BleDeviceFeatures.FeatureBatteryLevel.getBatteryLevelPercent(bleDevice).toInt()

            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                when (device.address) {
                    "0E:06:E5:75:F0:AE" -> {
                        address = "A Black #1"
                        //name = "C Black #1"
                        name = "CARD"
                        rssi = -42
                        rssiSmoothed = rssi
                    }
                    "0E:06:E5:E6:E7:AE" -> {
                        address = "B Green"
                        //name = "A Green"
                        name = "FNDR"
                        //rssi = -42
                        rssi = -24
                        rssiSmoothed = rssi
                    }
                    "0E:06:E5:E2:73:AF" -> {
                        address = "C Black #2"
                        //name = "B Black #2"
                        //name = "FNDR"
                        name = "CARD"
                        rssi = -42
                        rssiSmoothed = rssi
                    }
                    else -> {
                        address = device.address
                        name = device.name
                        rssi = bleScanResult.rssi
                        rssiSmoothed = bleScanResult.rssiSmoothed
                    }
                }
            }

            return DeviceInfo(
                    address,
                    name,
                    modelNumber,
                    rssi,
                    rssiSmoothed,
                    item.addedElapsedMillis,
                    item.lastUpdatedElapsedMillis,
                    item.timeoutRemainingMillis,
                    isClicked,
                    batteryPercent
            )
        }
        */
    }

    constructor(
        scanResult: android.bluetooth.le.ScanResult,
        rssiSmoothedCurrent: Int = 0
    ) : this(
        scanResult.device,
        scanResult.scanRecord,
        getDeviceNameOrScanRecordName(scanResult),
        scanResult.rssi,
        rssiSmoothedCurrent
    )

    constructor(
        scanResult: no.nordicsemi.android.support.v18.scanner.ScanResult,
        rssiSmoothedCurrent: Int = 0
    ) : this(
        scanResult.device,
        scanRecordFromBytes(scanResult),
        getDeviceNameOrScanRecordName(scanResult),
        scanResult.rssi,
        rssiSmoothedCurrent
    )

    constructor(
        device: com.idevicesinc.sweetblue.BleDevice,
        rssiSmoothedCurrent: Int = 0
    ) : this(
        device.native,
        scanRecordFromBytes(device.scanRecord),
        device.name_normalized,
        device.rssi,
        rssiSmoothedCurrent
    )

    constructor(
        device: BluetoothDevice,
        scanRecord: ScanRecord?,
        name: String,
        rssi: Int,
        rssiSmoothedCurrent: Int = 0
    ) {
        this.device = device
        this.macAddress = device.address
        this.macAddressLong = Utils.macAddressStringToLong(macAddress)
        this.scanRecord = scanRecord
        this.name = name
        this.rssi = rssi
        this.rssiSmoothedCurrent = rssiSmoothedCurrent
        update(device, scanRecord, name, rssi)
    }

    val macAddress: String
    val macAddressLong: Long

    var device: BluetoothDevice
        private set
    var scanRecord: ScanRecord?
        private set
    var name: String
        private set

    @Suppress("MemberVisibilityCanBePrivate")
    var rssi: Int
        private set

    val rssiSmoothed: Int
        get() = rssiSmoothedCurrent

    private var rssiSmoothedCurrent: Int = 0
    private var rssiSmoothedPrevious: Int = 0

    /**
     * The default Kotlin impl does not show the hashCode address; this one does.
     */
    override fun toString(): String {
        return Utils.getShortClassName(this) + "@" + Integer.toHexString(hashCode()) + "(" +
                "macAddress=$macAddress" +
                ", macAddressLong=$macAddressLong" +
                ", name=$name" +
                ", rssi=$rssi" +
                ", rssiSmoothed=$rssiSmoothed" +
                ")"
    }

    fun update(scanResult: android.bluetooth.le.ScanResult): Boolean {
        return update(
            scanResult.device,
            scanResult.scanRecord,
            getDeviceNameOrScanRecordName(scanResult),
            scanResult.rssi
        )
    }

    fun update(scanResult: no.nordicsemi.android.support.v18.scanner.ScanResult): Boolean {
        return update(
            scanResult.device,
            scanRecordFromBytes(scanResult),
            getDeviceNameOrScanRecordName(scanResult),
            scanResult.rssi
        )
    }

    fun update(device: com.idevicesinc.sweetblue.BleDevice): Boolean {
        return update(
            device.native,
            scanRecordFromBytes(device.scanRecord),
            device.name_normalized,
            device.rssi
        )
    }

    fun update(
        device: BluetoothDevice,
        scanRecord: ScanRecord?,
        name: String,
        rssi: Int
    ): Boolean {
        this.device = device
        this.scanRecord = scanRecord
        this.name = name

        @Suppress("NAME_SHADOWING") var rssi = rssi

        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssi=$rssi")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssiSmoothedCurrent=$rssiSmoothedCurrent")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssiSmoothedPrevious=$rssiSmoothedPrevious")
        if (rssi != 0) {
            if (rssiSmoothedCurrent != 0) {
                rssi = LowPassFilter.update(rssi.toLong(), rssiSmoothedCurrent.toLong()).toInt()
            }
        }
        rssiSmoothedPrevious = rssiSmoothedCurrent
        val changed = rssiSmoothedPrevious != rssi
        rssiSmoothedCurrent = rssi

        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssi=$rssi")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssiSmoothedCurrent=$rssiSmoothedCurrent")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssiSmoothedPrevious=$rssiSmoothedPrevious")

        return changed
    }
}