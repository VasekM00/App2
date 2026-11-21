package com.example.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Callback invoked immediately before launching an external Android system picker or chooser
 * so background re-lock does not fire spuriously for intentional file/share flows.
 */
val LocalExternalPickerGuard = compositionLocalOf<() -> Unit> { {} }
