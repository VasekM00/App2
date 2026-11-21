package com.example

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Ensures the target test device display is awake and keyguard dismissed before Compose UI tests
 * launch their activity so `createComposeRule()` can attach to a foreground window reliably.
 */
class WakeDeviceRule : TestWatcher() {

    override fun starting(description: Description) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard").forEach { cmd ->
            runCatching {
                uiAutomation.executeShellCommand(cmd)?.let { pfd ->
                    ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
                }
            }
        }
    }
}
