package com.github.paulpv.helloblescanner

import android.app.Application
import android.os.Looper

@Suppress("unused")
class MyApplication : Application() {
    val businessLogic = MyBusinessLogic(this, Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        businessLogic.initialize()
    }
}