package com.github.paulpv.helloblescanner

import android.app.Application

@Suppress("unused")
class MyApplication : Application() {
    val businessLogic = MyBusinessLogic()

    override fun onCreate() {
        super.onCreate()
        businessLogic.initialize()
    }
}