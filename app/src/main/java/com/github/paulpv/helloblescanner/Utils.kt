package com.github.paulpv.helloblescanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.Log
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {
    private const val TAG = "Utils"

    fun getClassName(
        className: String?,
        shortClassName: Boolean
    ): String {
        @Suppress("NAME_SHADOWING") var className = className
        if (className.isNullOrEmpty()) {
            className = "null"
        }
        if (shortClassName) {
            className = className.substring(className.lastIndexOf('.') + 1)
            className = className.substring(className.lastIndexOf('$') + 1)
        }
        return className
    }

    fun getShortClassName(className: String?) = getClassName(className, true)

    fun getShortClassName(o: Any?) = getShortClassName(o?.javaClass)

    fun getShortClassName(c: Class<*>?): String {
        val className = c?.name
        return getShortClassName(className)
    }

    //
    //
    //

    @JvmStatic
    fun getColor(context: Context, resId: Int) = getColor(context.resources, resId)

    @JvmStatic
    fun getColor(resources: Resources, resId: Int): Int {
        // https://developer.android.com/reference/android/content/res/Resources#getcolor
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 23) resources.getColor(
            resId,
            null
        ) else resources.getColor(resId)
    }

    //
    //
    //

    @JvmStatic
    fun isNullOrEmpty(value: String?) = value.isNullOrEmpty()

    @JvmStatic
    fun split(source: String, separator: String, limit: Int): Array<String> {
        if (isNullOrEmpty(source) || isNullOrEmpty(separator)) {
            return arrayOf(source)
        }
        var indexB = source.indexOf(separator)
        if (indexB == -1) {
            return arrayOf(source)
        }
        var indexA = 0
        var value: String
        val values = ArrayList<String>()
        while (indexB != -1 && (limit < 1 || values.size < limit - 1)) {
            value = source.substring(indexA, indexB)
            if (!isNullOrEmpty(value) || limit < 0) {
                values.add(value)
            }
            indexA = indexB + separator.length
            indexB = source.indexOf(separator, indexA)
        }
        indexB = source.length
        value = source.substring(indexA, indexB)
        if (!isNullOrEmpty(value) || limit < 0) {
            values.add(value)
        }
        return values.toTypedArray()
    }

    @JvmStatic
    fun padNumber(number: Long, ch: Char, minimumLength: Int): String {
        val s = StringBuilder(number.toString())
        while (s.length < minimumLength) {
            s.insert(0, ch)
        }
        return s.toString()
    }

    @JvmStatic
    fun formatNumber(number: Long, minimumLength: Int) = padNumber(number, '0', minimumLength)

    @Suppress("unused")
    @JvmStatic
    fun formatNumber(number: Double, leading: Int, trailing: Int): String {
        if (java.lang.Double.isNaN(number) || number == java.lang.Double.NEGATIVE_INFINITY || number == java.lang.Double.POSITIVE_INFINITY) {
            return number.toString()
        }

        // String.valueOf(1) is guaranteed to at least be of the form "1.0"
        val parts = split(number.toString(), ".", 0)
        while (parts[0].length < leading) {
            parts[0] = '0' + parts[0]
        }
        while (parts[1].length < trailing) {
            parts[1] = parts[1] + '0'
        }
        parts[1] = parts[1].substring(0, trailing)
        return parts[0] + '.'.toString() + parts[1]
    }

    /**
     * @param elapsedMillis elapsedMillis
     * @param maximumTimeUnit maximumTimeUnit
     * @return HH:MM:SS.MMM
     */
    @JvmStatic
    fun getTimeDurationFormattedString(
        elapsedMillis: Long,
        maximumTimeUnit: TimeUnit? = null
    ): String {
        // TODO:(pv) Get to work for negative values?
        // TODO:(pv) Handle zero value
        @Suppress("NAME_SHADOWING") var elapsedMillis = elapsedMillis
        @Suppress("NAME_SHADOWING") var maximumTimeUnit = maximumTimeUnit
        if (maximumTimeUnit == null) {
            maximumTimeUnit = TimeUnit.HOURS
        }
        if (maximumTimeUnit > TimeUnit.DAYS) {
            throw IllegalArgumentException("maximumTimeUnit must be null or <= TimeUnit.DAYS")
        }
        if (maximumTimeUnit < TimeUnit.MILLISECONDS) {
            throw IllegalArgumentException("maximumTimeUnit must be null or >= TimeUnit.MILLISECONDS")
        }
        val sb = StringBuilder()
        if (maximumTimeUnit >= TimeUnit.DAYS) {
            val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
            sb.append(formatNumber(days, 2)).append(':')
            if (days > 0) {
                elapsedMillis -= TimeUnit.DAYS.toMillis(days)
            }
        }
        if (maximumTimeUnit >= TimeUnit.HOURS) {
            val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
            sb.append(formatNumber(hours, 2)).append(':')
            if (hours > 0) {
                elapsedMillis -= TimeUnit.HOURS.toMillis(hours)
            }
        }
        if (maximumTimeUnit >= TimeUnit.MINUTES) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
            sb.append(formatNumber(minutes, 2)).append(':')
            if (minutes > 0) {
                elapsedMillis -= TimeUnit.MINUTES.toMillis(minutes)
            }
        }
        if (maximumTimeUnit >= TimeUnit.SECONDS) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis)
            sb.append(formatNumber(seconds, 2)).append('.')
            if (seconds > 0) {
                elapsedMillis -= TimeUnit.SECONDS.toMillis(seconds)
            }
        }
        sb.append(formatNumber(elapsedMillis, 3))
        return sb.toString()
    }

    //
    //
    //

    @Suppress("MemberVisibilityCanBePrivate")
    @JvmStatic
    fun isBluetoothSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    @JvmStatic
    fun isBluetoothLowEnergySupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * @param context
     * @return null if Bluetooth is not supported
     */
    @Suppress("MemberVisibilityCanBePrivate")
    @JvmStatic
    fun getBluetoothManager(context: Context): BluetoothManager? {
        return if (!isBluetoothSupported(context)) null else context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    /**
     * Per: http://developer.android.com/reference/android/bluetooth/BluetoothAdapter.html
     * "To get a BluetoothAdapter representing the local Bluetooth adapter, when running on JELLY_BEAN_MR1 and below,
     * call the static getDefaultAdapter() method; when running on JELLY_BEAN_MR2 and higher, retrieve it through
     * getSystemService(String) with BLUETOOTH_SERVICE. Fundamentally, this is your starting point for all Bluetooth
     * actions."
     *
     * @return null if Bluetooth is not supported
     */
    @SuppressLint("ObsoleteSdkInt")
    @JvmStatic
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return if (!isBluetoothSupported(context)) null else if (Build.VERSION.SDK_INT <= 17) BluetoothAdapter.getDefaultAdapter() else getBluetoothManager(
            context
        )?.adapter
    }

    @JvmStatic
    fun isBluetoothAdapterEnabled(bluetoothAdapter: BluetoothAdapter?): Boolean {
        return try {
            // NOTE:(pv) Known to sometimes throw DeadObjectException
            //  https://code.google.com/p/android/issues/detail?id=67272
            //  https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
            bluetoothAdapter != null && bluetoothAdapter.isEnabled
        } catch (e: Exception) {
            Log.w(TAG, "isBluetoothAdapterEnabled: bluetoothAdapter.isEnabled()", e)
            false
        }
    }

    /**
     * @param bluetoothAdapter
     * @param on
     * @return true if successfully set; false if the set failed
     * @see <ul>
     * <li><a href="https://code.google.com/p/android/issues/detail?id=67272">https://code.google.com/p/android/issues/detail?id=67272</a></li>
     * <li><a href="https://github.com/RadiusNetworks/android-ibeacon-service/issues/16">https://github.com/RadiusNetworks/android-ibeacon-service/issues/16</a></li>
     * </ul>
     */
    @Suppress("unused")
    @JvmStatic
    fun bluetoothAdapterEnable(bluetoothAdapter: BluetoothAdapter?, on: Boolean): Boolean {
        // TODO:(pv) Known to sometimes throw DeadObjectException
        //  https://code.google.com/p/android/issues/detail?id=67272
        //  https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
        return bluetoothAdapter != null &&
                if (on) {
                    try {
                        bluetoothAdapter.enable()
                        true
                    } catch (e: Exception) {
                        Log.v(TAG, "bluetoothAdapterEnable: bluetoothAdapter.enable()", e)
                        false
                    }
                } else {
                    try {
                        bluetoothAdapter.disable()
                        true
                    } catch (e: Exception) {
                        Log.v(TAG, "bluetoothAdapterEnable: bluetoothAdapter.disable()", e)
                        false
                    }
                }
    }

    @JvmStatic
    fun callbackTypeToString(callbackType: Int): String {
        return when (callbackType) {
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> "CALLBACK_TYPE_FIRST_MATCH"
            ScanSettings.CALLBACK_TYPE_MATCH_LOST -> "CALLBACK_TYPE_MATCH_LOST"
            ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> "CALLBACK_TYPE_ALL_MATCHES"
            else -> "CALLBACK_TYPE_UNKNOWN"
        } + "($callbackType)"
    }
}