package com.huhst.jiaowu

import android.app.Application
import com.huhst.jiaowu.di.AppContainer

class JiaowuApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
