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
import kotlin.reflect.KClass

object Utils {
    private val TAG: String = TAG(Utils::class)

    @Suppress("MemberVisibilityCanBePrivate")
    @JvmStatic
    val LOG_TAG_LENGTH_LIMIT_PRE_API24 = 23

    @Suppress("MemberVisibilityCanBePrivate")
    @JvmStatic
    val LOG_TAG_LENGTH_LIMIT_POST_API23 = -1

    /**
     * Per [android.util.Log.isLoggable] Throws description:
     * http://developer.android.com/reference/android/util/Log.html#isLoggable(java.lang.String, int)
     * '''
     * IllegalArgumentException is thrown if the tag.length() > 23 for Nougat (7.0) releases (API <= 23) and prior, there is no tag limit of concern after this API level.
     * '''
     */
    @Suppress("MemberVisibilityCanBePrivate")
    @JvmStatic
    val LOG_TAG_LENGTH_LIMIT =
        if (Build.VERSION.SDK_INT < 24) LOG_TAG_LENGTH_LIMIT_PRE_API24 else LOG_TAG_LENGTH_LIMIT_POST_API23

    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(o: Any?) = TAG(o, LOG_TAG_LENGTH_LIMIT)

    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(o: Any?, limit: Int) = TAG(o?.javaClass, limit)

    fun TAG(c: KClass<*>?, limit: Int = LOG_TAG_LENGTH_LIMIT) = TAG(c?.java, limit)

    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(c: Class<*>?) = TAG(c, LOG_TAG_LENGTH_LIMIT)

    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(c: Class<*>?, limit: Int) = TAG(getShortClassName(c), limit)

    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(tag: String) = TAG(tag, LOG_TAG_LENGTH_LIMIT)

    /**
     * Limits the tag length to [#LOG_TAG_LENGTH_LIMIT]
     * Example: "ReallyLongClassName" to "ReallyLo…lassName"
     *
     * @param tag
     * @return the tag limited to [#LOG_TAG_LENGTH_LIMIT]
     */
    @Suppress("FunctionName")
    @JvmStatic
    fun TAG(tag: String, limit: Int): String {
        if (limit == -1) {
            return tag
        }

        var length = tag.length
        if (length <= limit) {
            return "$tag${'\u00A0'.toString().repeat(limit - length)}"
        }

        @Suppress("NAME_SHADOWING")
        val tag = tag.substring(tag.lastIndexOf("$") + 1, length)
        length = tag.length
        if (length <= limit) {
            return "$tag${'\u00A0'.toString().repeat(limit - length)}"
        }
        val half = limit / 2
        return tag.substring(0, half) + '…' + tag.substring(length - half)
    }

    @JvmStatic
    fun getClassName(className: String?, shortClassName: Boolean): String {
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

    @JvmStatic
    fun getShortClassName(className: String?) = getClassName(className, true)

    @JvmStatic
    fun getShortClassName(o: Any?) = getShortClassName(o?.javaClass)

    @JvmStatic
    fun getShortClassName(c: Class<*>?): String {
        val className = c?.name
        return getShortClassName(className)
    }

    /**
     * @see Object.toString
     */
    @JvmStatic
    fun defaultToString(o: Any?): String {
        return if (o != null) "${getShortClassName(o)}@${Integer.toHexString(o.hashCode())}" else "null"
    }

    /**
     * Identical to [.repr], but grammatically intended for Strings.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    @JvmStatic
    fun quote(value: Any?) = repr(value, false)

    /**
     * Identical to [.quote], but grammatically intended for Objects.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    @JvmStatic
    fun repr(value: Any?) = repr(value, false)

    /**
     * @param value    value
     * @param typeOnly typeOnly
     * @return "null", or '\"' + value.toString + '\"', or value.toString(), or getShortClassName(value)
     */
    @JvmStatic
    fun repr(value: Any?, typeOnly: Boolean): String {
        if (value == null) return "null"
        if (value is String) return "\"$value\""
        if (typeOnly) {
            return getShortClassName(value)
        }
        if (value is Array<*>) return toString(value)
        if (value is ByteArray) return toHexString(value)
        return value.toString()
    }

    @JvmStatic
    fun toString(items: Array<*>?): String {
        val sb = StringBuilder()
        if (items == null) {
            sb.append("null")
        } else {
            sb.append('[')
            for (i in items.indices) {
                if (i != 0) {
                    sb.append(", ")
                }
                val item = items[i]
                sb.append(repr(item))
            }
            sb.append(']')
        }
        return sb.toString()
    }

    @JvmStatic
    fun toHexString(value: String) = toHexString(value.toByteArray())

    @JvmStatic
    fun toHexString(bytes: ByteArray?) = toHexString(bytes, true)

    @JvmStatic
    fun toHexString(bytes: ByteArray?, asByteArray: Boolean) =
        if (bytes == null) "null" else toHexString(bytes, 0, bytes.size, asByteArray)

    @JvmStatic
    fun toHexString(bytes: ByteArray?, offset: Int, count: Int) = toHexString(bytes, offset, count, true)

    @JvmStatic
    fun toHexString(bytes: ByteArray?, offset: Int, count: Int, asByteArray: Boolean): String {
        if (bytes == null) {
            return "null"
        }
        val hexChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val sb = java.lang.StringBuilder()
        if (asByteArray) {
            for (i in offset until count) {
                if (i != offset) {
                    sb.append('-')
                }
                sb.append(hexChars[(0x000000f0 and bytes[i].toInt()) shr 4])
                sb.append(hexChars[(0x0000000f and bytes[i].toInt())])
            }
        } else {
            for (i in count - 1 downTo 0) {
                sb.append(hexChars[(0x000000f0 and bytes[i].toInt()) shr 4])
                sb.append(hexChars[(0x0000000f and bytes[i].toInt())])
            }
        }
        return sb.toString()
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

    //
    //
    //

    fun macAddressStringToStrippedLowerCaseString(macAddress: String?): String {
        return (macAddress ?: "00:00:00:00:00:00").replace(":", "").toLowerCase(Locale.ROOT)
    }

    fun macAddressStringToLong(macAddress: String?): Long {
        return java.lang.Long.parseLong(macAddressStringToStrippedLowerCaseString(macAddress), 16)
    }
}