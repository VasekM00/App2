package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.LocalExternalPickerGuard
import com.example.ui.theme.MartinuFinancialsTheme
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle()
            val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
            val isPreferencesHydrated by viewModel.isPreferencesHydrated.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            var hasEvaluatedColdLock by rememberSaveable { mutableStateOf(false) }
            var isLocked by rememberSaveable { mutableStateOf(false) }
            var authErrorMessage by remember { mutableStateOf<String?>(null) }
            var suppressNextBackgroundLock by remember { mutableStateOf(false) }
            var isPromptActive by remember { mutableStateOf(false) }

            val triggerBiometricPrompt = remember(this) {
                {
                    if (!isPromptActive) {
                        isPromptActive = true
                        authErrorMessage = null
                        val executor = ContextCompat.getMainExecutor(this)
                        val prompt = BiometricPrompt(
                            this,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    isPromptActive = false
                                    isLocked = false
                                    authErrorMessage = null
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    isPromptActive = false
                                    val msg = errString.toString().trim()
                                    if (msg.isNotBlank() && errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    ) {
                                        authErrorMessage = msg
                                    }
                                }
                            }
                        )
                        val builder = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Martinu Financials")
                            .setSubtitle("Authenticate to access your household data")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            builder.setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            builder.setDeviceCredentialAllowed(true)
                        }
                        try {
                            prompt.authenticate(builder.build())
                        } catch (e: Exception) {
                            isPromptActive = false
                            authErrorMessage = e.localizedMessage ?: "Biometric authentication unavailable."
                        }
                    }
                }
            }

            LaunchedEffect(isPreferencesHydrated, biometricLockEnabled) {
                if (!biometricLockEnabled) {
                    isLocked = false
                    authErrorMessage = null
                    if (isPreferencesHydrated) {
                        hasEvaluatedColdLock = true
                    }
                } else if (isPreferencesHydrated && !hasEvaluatedColdLock) {
                    hasEvaluatedColdLock = true
                    isLocked = true
                    triggerBiometricPrompt()
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, biometricLockEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            if (biometricLockEnabled && !suppressNextBackgroundLock && !isPromptActive) {
                                isLocked = true
                            }
                        }
                        Lifecycle.Event.ON_START -> {
                            if (isLocked && biometricLockEnabled && !isPromptActive) {
                                triggerBiometricPrompt()
                            }
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            suppressNextBackgroundLock = false
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(flagSecureEnabled) {
                if (flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
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
                when {
                    !isPreferencesHydrated -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    }
                    isLocked -> {
                        AppLockOverlay(
                            onUnlockClick = triggerBiometricPrompt,
                            authErrorMessage = authErrorMessage
                        )
                    }
                    else -> {
                        CompositionLocalProvider(
                            LocalExternalPickerGuard provides { suppressNextBackgroundLock = true }
                        ) {
                            MainScreen(
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                themeMode = themeMode,
                                onChangeThemeMode = { newMode ->
                                    viewModel.setThemeMode(newMode)
                                },
                                onToggleDarkTheme = {
                                    val nextMode = when (themeMode) {
                                        "LIGHT" -> "DARK"
                                        "DARK" -> "SYSTEM"
                                        else -> if (systemDark) "LIGHT" else "DARK"
                                    }
                                    viewModel.setThemeMode(nextMode)
                                }
                            )
                        }
                    }
                }
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
        val canonicalDataDir by lazy(LazyThreadSafetyMode.NONE) {
            runCatching { File(applicationInfo.dataDir).canonicalPath }.getOrNull()
        }
        val safeUris = uris.filter { uri ->
            if (!"file".equals(uri.scheme, ignoreCase = true)) {
                true
            } else {
                val targetDir = canonicalDataDir
                val canonicalTarget = uri.path?.let { runCatching { File(it).canonicalPath }.getOrNull() }
                canonicalTarget != null && targetDir != null &&
                    canonicalTarget != targetDir &&
                    !canonicalTarget.startsWith(targetDir + File.separator)
            }
        }
        if (safeUris.isNotEmpty()) {
            viewModel.importStatements(safeUris)
        }
    }
}
