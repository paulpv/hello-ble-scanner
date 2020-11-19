package com.github.paulpv.helloblescanner

import android.annotation.SuppressLint
import android.content.Context
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import java.util.*
import java.util.concurrent.TimeUnit

class DevicesViewHolder(val context: Context, itemView: ViewGroup) :
        BindableViewHolder<DeviceInfo>(itemView) {
    //companion object {
    //    private val TAG = TAG(DevicesViewHolder::class)
    //}

    private val groupDeviceCell: ViewGroup = itemView.findViewById(R.id.groupDeviceCell)
    private val labelAddress: TextView = itemView.findViewById(R.id.labelAddress)
    private val labelAge: TextView = itemView.findViewById(R.id.labelAge)
    private val labelLastSeen: TextView = itemView.findViewById(R.id.labelLastSeen)
    private val labelTimeoutRemaining: TextView = itemView.findViewById(R.id.labelTimeoutRemaining)
    private val labelName: TextView = itemView.findViewById(R.id.labelName)
    private val labelRssiReal: TextView = itemView.findViewById(R.id.labelRssiReal)
    private val labelRssiAverage: TextView = itemView.findViewById(R.id.labelRssiAverage)
    private val labelBatteryPercent: TextView = itemView.findViewById(R.id.labelBatteryPercent)

    @SuppressLint("SetTextI18n")
    override fun bindTo(item: DeviceInfo, clickListener: OnClickListener) {
        super.bindTo(item, clickListener)
        val backgroundColor = if (item.isClicked) R.color.colorDeviceCellClicked else R.color.colorDeviceCell
        groupDeviceCell.setBackgroundColor(Utils.getColor(context, backgroundColor))
        labelAddress.text = item.macAddress
        labelAge.text = "age=${Utils.getTimeDurationFormattedString(item.addedElapsedMillis)}"
        labelLastSeen.text = "seen=${Utils.getTimeDurationFormattedString(item.lastUpdatedElapsedMillis, TimeUnit.MINUTES)}"
        labelTimeoutRemaining.text = "remain=${Utils.getTimeDurationFormattedString(item.timeoutRemainingMillis, TimeUnit.MINUTES)}"
        labelName.text = item.name
        val locale = Locale.getDefault()
        labelRssiReal.text = String.format(locale, "real=%04d", item.signalStrengthRealtime)
        labelRssiAverage.text = String.format(locale, "avg=%04d", item.signalStrengthSmoothed)
        labelBatteryPercent.text = String.format(locale, "batt= %02d%%", item.batteryPercent)
    }
}
