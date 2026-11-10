package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MartinuFinancialsTheme

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
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
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
        val uris = mutableListOf<Uri>()
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
        if (uris.isNotEmpty()) {
            viewModel.importStatements(uris)
        }
    }
}
