package com.github.paulpv.helloblescanner

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
import com.github.paulpv.helloblescanner.collections.ExpiringIterableLongSparseArray
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
        devicesAdapter.setEventListener(object : DevicesAdapter.EventListener<BleScanResult> {
            override fun onItemSelected(item: BleScanResult) = this@MainActivity.onItemSelected(item)
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

        var menuItem = menu?.findItem(
            when (businessLogic.scannerType) {
                MyBusinessLogic.ScannerTypes.Native -> R.id.action_scanner_type_native
                MyBusinessLogic.ScannerTypes.Nordic -> R.id.action_scanner_type_nordic
                MyBusinessLogic.ScannerTypes.SweetBlue -> R.id.action_scanner_type_sweetblue
            }
        )
        if (menuItem != null) {
            menuItem.isChecked = true
        }

        menuItem = when {
            businessLogic.USE_SCAN_CALLBACK -> menu?.findItem(R.id.action_scanner_mode_scancallback)
            businessLogic.USE_SCAN_PENDING_INTENT -> menu?.findItem(R.id.action_scanner_mode_pendingintent)
            else -> null
        }
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
            R.id.action_scanner_mode_pendingintent -> {
                businessLogic.USE_SCAN_PENDING_INTENT = true
            }
            R.id.action_scanner_mode_scancallback -> {
                businessLogic.USE_SCAN_CALLBACK = true
            }
            R.id.action_clear -> {
                businessLogic.clear()
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

    private fun onScanningPermissionDenied() {
        switchScan?.isChecked = false
        val view: View = window.decorView.findViewById(android.R.id.content)
        Snackbar.make(view, "Missing Required Permissions", Snackbar.LENGTH_LONG).show()
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                scanStartAfterPermissionsGranted()
            } else {
                onScanningPermissionDenied()
            }
        }

    private fun scanStart(start: Boolean) {
        if (start) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                when {
                    MyBusinessLogic.PERMISSIONS.all {
                        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                    } -> scanStartAfterPermissionsGranted()
                    MyBusinessLogic.PERMISSIONS.all {
                        shouldShowRequestPermissionRationale(it)
                    } -> onScanningPermissionDenied()
                    else -> {
                        onScanningPermissionDenied()
                        requestMultiplePermissions.launch(MyBusinessLogic.PERMISSIONS)
                    }
                }
            } else {
                scanStartAfterPermissionsGranted()
            }
        } else {
            businessLogic.scanStop()
        }
    }

    private fun scanStartAfterPermissionsGranted() {
        switchScan?.isChecked = businessLogic.scanStart()
        devicesAdapter?.autoUpdateVisibleItems(true)
    }

    //
    //
    //

    @SuppressLint("SetTextI18n")
    private fun updateScanCount() {
        //text_scan_count.text = "(${devicesAdapter!!.itemCount})"
    }

    override fun onScanResultAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultAdded($item)")
        devicesAdapter.add(item.value)
        //val device = getDevice(item)
        //device?.gattHandler?.addListener(gattHandlerListener)
        updateScanCount()
    }

    override fun onScanResultUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultUpdated($item)")
        devicesAdapter.add(item.value)
    }

    override fun onScanResultRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onScanResultRemovedRemoved($item)")
        devicesAdapter.remove(item.value)
        //val device = getDevice(item)
        //device?.gattHandler?.removeListener(gattHandlerListener)
        updateScanCount()
    }

    //
    //
    //

    private fun onItemSelected(item: BleScanResult) {
        Log.i(TAG, "onItemSelected: item=$item")
        //...
    }
}