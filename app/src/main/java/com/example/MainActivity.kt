package com.example

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
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE)
        val initialDarkTheme = if (prefs.contains("is_dark_theme")) prefs.getBoolean("is_dark_theme", false) else null

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember {
                mutableStateOf(initialDarkTheme ?: systemDark)
            }

            MartinuFinancialsTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = {
                        val next = !isDarkTheme
                        isDarkTheme = next
                        prefs.edit { putBoolean("is_dark_theme", next) }
                    }
                )
            }
        }
    }
}
