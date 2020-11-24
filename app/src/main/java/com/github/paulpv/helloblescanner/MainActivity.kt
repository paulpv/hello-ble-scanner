package com.github.paulpv.helloblescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.paulpv.helloblescanner.scanners.ScannerAbstract
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), ScannerAbstract.Callbacks {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var businessLogic: MyBusinessLogic

    private lateinit var switchScan: SwitchCompat
    private lateinit var devicesAdapter: DevicesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        businessLogic = (application as MyApplication).businessLogic

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        devicesAdapter = DevicesAdapter(this, SortBy.Address)
        devicesAdapter.setEventListener(object : DevicesAdapter.EventListener<DeviceInfo> {
            override fun onItemSelected(item: DeviceInfo) = this@MainActivity.onItemSelected(item)
        })

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val dividerItemDecoration = DividerItemDecoration(this, layoutManager.orientation)
        with(scan_results) {
            setLayoutManager(layoutManager)
            addItemDecoration(dividerItemDecoration)
            setHasFixedSize(true)
            itemAnimator = null
            adapter = devicesAdapter
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!super.onCreateOptionsMenu(menu)) {
            return false
        }

        menuInflater.inflate(R.menu.main_activity, menu)

        MenuCompat.setGroupDividerEnabled(menu, true)

        val menuItem = menu.findItem(R.id.action_toggle_scanning)
        if (menuItem != null) {
            val actionView = menuItem.actionView
            if (actionView != null) {
                val textView = actionView.findViewById<TextView>(R.id.action_switch_text)
                if (textView != null) {
                    val title = menuItem.title
                    textView.text = title
                    textView.visibility = View.VISIBLE
                }
                switchScan = actionView.findViewById(R.id.action_switch_control)
                switchScan.isChecked = businessLogic.isScanStarted
                switchScan.setOnCheckedChangeListener { _, isChecked ->
                    scanStart(isChecked)
                }
            }
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (!super.onPrepareOptionsMenu(menu)) {
            return false
        }

        val menuItem = menu?.findItem(
            when (businessLogic.scannerType) {
                MyBusinessLogic.ScannerTypes.Native -> R.id.action_scanner_type_native
                MyBusinessLogic.ScannerTypes.Nordic -> R.id.action_scanner_type_nordic
                MyBusinessLogic.ScannerTypes.SweetBlue -> R.id.action_scanner_type_sweetblue
            }
        )
        if (menuItem != null) {
            menuItem.isChecked = true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }

        var consume = true
        when (item.itemId) {
            R.id.action_scanner_type_native -> {
                businessLogic.scannerType = MyBusinessLogic.ScannerTypes.Native
            }
            R.id.action_scanner_type_nordic -> {
                businessLogic.scannerType = MyBusinessLogic.ScannerTypes.Nordic
            }
            R.id.action_scanner_type_sweetblue -> {
                businessLogic.scannerType = MyBusinessLogic.ScannerTypes.SweetBlue
            }
            else -> {
                consume = false
            }
        }
        return consume
    }

    //
    //
    //

    private val requestScanningPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scanStart(true)
        } else {
            onScanningPermissionDenied()
        }
    }

    private fun onScanningPermissionDenied() {
        switchScan.isChecked = false
        val view: View = window.decorView.findViewById(android.R.id.content)
        Snackbar.make(view, "Location permission required", Snackbar.LENGTH_LONG).show()
    }

    private fun scanStart(start: Boolean) {
        if (start) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    businessLogic.scanStart()
                    // TODO:(pv) if failed then set buttonView.isChecked = false
                    devicesAdapter.autoUpdateVisibleItems(start)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    onScanningPermissionDenied()
                }
                else -> {
                    requestScanningPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        } else {
            businessLogic.scanStop()
        }
    }

    //
    //
    //

    @SuppressLint("SetTextI18n")
    private fun updateScanCount() {
        //text_scan_count.text = "(${devicesAdapter!!.itemCount})"
    }

    override fun onScanningStarted() {
        Log.v(TAG, "onScanningStarted()")
    }

    override fun onScanningStopped() {
        Log.v(TAG, "onScanningStopped()")
    }

    override fun onDeviceAdded(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceAdded($deviceInfo)")
        devicesAdapter.add(deviceInfo)
        //val device = getDevice(item)
        //device?.gattHandler?.addListener(gattHandlerListener)
        updateScanCount()
    }

    override fun onDeviceUpdated(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceUpdated($deviceInfo)")
        devicesAdapter.add(deviceInfo)
    }

    override fun onDeviceRemoved(deviceInfo: DeviceInfo) {
        Log.v(TAG, "onDeviceRemoved($deviceInfo)")
        devicesAdapter.remove(deviceInfo)
        //val device = getDevice(item)
        //device?.gattHandler?.removeListener(gattHandlerListener)
        updateScanCount()
    }

    //
    //
    //

    private fun onItemSelected(item: DeviceInfo) {
        Log.i(TAG, "onItemSelected: item=$item")
        //...
    }
}