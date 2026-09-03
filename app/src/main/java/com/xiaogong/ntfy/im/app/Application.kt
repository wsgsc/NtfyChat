package com.xiaogong.ntfy.im.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.util.Log

class Application : Application() {
    val repository by lazy {
        val repository = Repository.getInstance(applicationContext)
        if (repository.getRecordLogs()) {
            Log.setRecord(true)
        }
        repository
    }

    override fun onCreate() {
        super.onCreate()
        if (repository.getDynamicColorsEnabled()) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
