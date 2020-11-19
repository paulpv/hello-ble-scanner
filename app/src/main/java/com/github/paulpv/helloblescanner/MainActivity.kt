package com.github.paulpv.helloblescanner

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
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

        devicesAdapter = DevicesAdapter(this, SortBy.SignalLevelRssi)
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
                    if (isChecked) {
                        businessLogic.scanStart()
                        // TODO:(pv) if failed then buttonView.isChecked = false
                    } else {
                        businessLogic.scanStop()
                    }
                    devicesAdapter.autoUpdateVisibleItems(isChecked)
                }
            }
        }

        return true
    }

    private fun onItemSelected(item: DeviceInfo) {
        Log.i(TAG, "onItemSelected: item=$item")
        //...
    }
}