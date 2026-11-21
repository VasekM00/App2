package com.example

import android.app.Application
import android.os.Build
import android.os.StrictMode

class MartinuFinancialsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
        if (BuildConfig.DEBUG && !isRobolectric) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
