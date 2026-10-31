package com.bhupendra.disciplineengine

import android.app.Application
import android.content.Context

class DisciplineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}

