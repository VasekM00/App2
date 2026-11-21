package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.edit
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MartinuFinancialsTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        val prefs = getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE)
        val initialThemeMode = prefs.getString("theme_mode", null) ?: if (prefs.contains("is_dark_theme")) {
            if (prefs.getBoolean("is_dark_theme", false)) "DARK" else "LIGHT"
        } else "SYSTEM"

        setContent {
            val systemDark = isSystemInDarkTheme()
            var themeMode by rememberSaveable { mutableStateOf(initialThemeMode) }
            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            MartinuFinancialsTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    themeMode = themeMode,
                    onChangeThemeMode = { newMode ->
                        themeMode = newMode
                        prefs.edit {
                            putString("theme_mode", newMode)
                            putBoolean("is_dark_theme", when (newMode) {
                                "LIGHT" -> false
                                "DARK" -> true
                                else -> systemDark
                            })
                        }
                    },
                    onToggleDarkTheme = {
                        val nextMode = when (themeMode) {
                            "LIGHT" -> "DARK"
                            "DARK" -> "SYSTEM"
                            else -> if (systemDark) "LIGHT" else "DARK"
                        }
                        themeMode = nextMode
                        prefs.edit {
                            putString("theme_mode", nextMode)
                            putBoolean("is_dark_theme", when (nextMode) {
                                "LIGHT" -> false
                                "DARK" -> true
                                else -> systemDark
                            })
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return

        val uris = linkedSetOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) uris.add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (streamUris != null) uris.addAll(streamUris.filterNotNull())
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
        }
        if (intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
        }
        val canonicalDataDir = runCatching { File(applicationInfo.dataDir).canonicalPath }.getOrNull()
        val safeUris = uris.filter { uri ->
            if (!"file".equals(uri.scheme, ignoreCase = true)) {
                true
            } else {
                val canonicalTarget = uri.path?.let { runCatching { File(it).canonicalPath }.getOrNull() }
                canonicalTarget != null && canonicalDataDir != null &&
                    canonicalTarget != canonicalDataDir &&
                    !canonicalTarget.startsWith(canonicalDataDir + File.separator)
            }
        }
        if (safeUris.isNotEmpty()) {
            viewModel.importStatements(safeUris)
        }
    }
}
