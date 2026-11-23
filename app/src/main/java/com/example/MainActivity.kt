package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.ui.components.canAuthenticateWithDevice
import com.example.ui.theme.MartinuFinancialsTheme
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private var onAuthSucceededCallback: (() -> Unit)? = null
    private var onAuthErrorCallback: ((Int, CharSequence) -> Unit)? = null

    companion object {
        private const val PICKER_GUARD_WINDOW_MS = 60_000L

        // Process-scoped: rememberSaveable state survives process death, so a restored
        // hasEvaluatedColdLock/isLocked pair would otherwise skip the cold-lock prompt entirely
        // and reopen the app with all financial data unlocked. This flag is false in every
        // fresh process and forces the cold-lock evaluation to run once per process lifetime.
        @Volatile
        private var coldLockEvaluatedThisProcess = false

        internal fun shouldLockOnStop(
            biometricEnabled: Boolean,
            isChangingConfigurations: Boolean,
            isPickerGuardActive: Boolean,
            isPromptActive: Boolean,
            canAuthenticateWithDevice: Boolean
        ): Boolean =
            biometricEnabled &&
                !isChangingConfigurations &&
                !isPickerGuardActive &&
                !isPromptActive &&
                canAuthenticateWithDevice

        internal fun shouldProcessPendingIncomingImports(
            isPreferencesHydrated: Boolean,
            hasEvaluatedColdLock: Boolean,
            biometricEnabled: Boolean,
            isLocked: Boolean
        ): Boolean =
            isPreferencesHydrated &&
                hasEvaluatedColdLock &&
                (!biometricEnabled || !isLocked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSucceededCallback?.invoke()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onAuthErrorCallback?.invoke(errorCode, errString)
                }
            }
        )

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle()
            val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
            val isPreferencesHydrated by viewModel.isPreferencesHydrated.collectAsStateWithLifecycle()
            val pendingIncomingUris by viewModel.pendingIncomingUris.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            var hasEvaluatedColdLock by rememberSaveable { mutableStateOf(false) }
            var isLocked by rememberSaveable { mutableStateOf(false) }
            var authErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
            var pickerGuardDeadlineMs by rememberSaveable { mutableLongStateOf(0L) }
            var isPromptActive by rememberSaveable { mutableStateOf(false) }

            onAuthSucceededCallback = {
                isPromptActive = false
                isLocked = false
                authErrorMessage = null
            }
            onAuthErrorCallback = { errorCode, errString ->
                isPromptActive = false
                val msg = errString.toString().trim()
                if (msg.isNotBlank() && errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    authErrorMessage = msg
                }
            }

            val triggerBiometricPrompt = remember(this) {
                {
                    if (!canAuthenticateWithDevice(this)) {
                        isPromptActive = false
                        isLocked = false
                        authErrorMessage = null
                        viewModel.setBiometricLockEnabled(false)
                        viewModel.showMessage("Device screen lock was removed; app lock was disabled so you are not locked out.")
                    } else if (!isPromptActive) {
                        isPromptActive = true
                        authErrorMessage = null
                        val builder = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Martinu Financials")
                            .setSubtitle("Authenticate to access your household data")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Class-3 (strong) biometrics or device credential only: a finance app
                            // must not be unlockable with weak (Class-2) biometrics.
                            builder.setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            builder.setDeviceCredentialAllowed(true)
                        }
                        try {
                            biometricPrompt.authenticate(builder.build())
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
                        coldLockEvaluatedThisProcess = true
                    }
                } else if (isPreferencesHydrated && !coldLockEvaluatedThisProcess) {
                    // Gate on the process-scoped flag, NOT on the saveable hasEvaluatedColdLock:
                    // after process death the saveable flag is restored as true, which must not
                    // skip the lock prompt.
                    coldLockEvaluatedThisProcess = true
                    hasEvaluatedColdLock = true
                    if (!canAuthenticateWithDevice(this@MainActivity)) {
                        isLocked = false
                        viewModel.setBiometricLockEnabled(false)
                        viewModel.showMessage("Device screen lock is not set; app lock disabled.")
                    } else {
                        isLocked = true
                        triggerBiometricPrompt()
                    }
                } else if (isPreferencesHydrated && isLocked && !canAuthenticateWithDevice(this@MainActivity)) {
                    isLocked = false
                    isPromptActive = false
                    viewModel.setBiometricLockEnabled(false)
                    viewModel.showMessage("Device screen lock was removed; app lock disabled.")
                }
            }

            LaunchedEffect(isPreferencesHydrated, hasEvaluatedColdLock, biometricLockEnabled, isLocked, pendingIncomingUris) {
                if (pendingIncomingUris.isNotEmpty() &&
                    shouldProcessPendingIncomingImports(
                        isPreferencesHydrated = isPreferencesHydrated,
                        hasEvaluatedColdLock = hasEvaluatedColdLock && coldLockEvaluatedThisProcess,
                        biometricEnabled = biometricLockEnabled,
                        isLocked = isLocked
                    )
                ) {
                    val uris = viewModel.consumeIncomingImportUris()
                    if (uris.isNotEmpty()) {
                        viewModel.importStatements(uris)
                    }
                }
            }

            val latestBiometricEnabled by rememberUpdatedState(biometricLockEnabled)
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            val now = SystemClock.elapsedRealtime()
                            val guardActive = pickerGuardDeadlineMs > now
                            if (shouldLockOnStop(
                                    biometricEnabled = latestBiometricEnabled,
                                    isChangingConfigurations = isChangingConfigurations,
                                    isPickerGuardActive = guardActive,
                                    isPromptActive = isPromptActive,
                                    canAuthenticateWithDevice = canAuthenticateWithDevice(this@MainActivity)
                                )
                            ) {
                                isLocked = true
                            }
                        }
                        Lifecycle.Event.ON_START -> {
                            if (isLocked && latestBiometricEnabled) {
                                if (!canAuthenticateWithDevice(this@MainActivity)) {
                                    isLocked = false
                                    isPromptActive = false
                                    viewModel.setBiometricLockEnabled(false)
                                    viewModel.showMessage("Device screen lock was removed; app lock disabled.")
                                } else if (!isPromptActive) {
                                    triggerBiometricPrompt()
                                }
                            }
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            pickerGuardDeadlineMs = 0L
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val shouldSecureWindow = flagSecureEnabled || (biometricLockEnabled && isLocked)
            SideEffect {
                if (shouldSecureWindow) {
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
                        SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
                        )
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
                            LocalExternalPickerGuard provides {
                                pickerGuardDeadlineMs = SystemClock.elapsedRealtime() + PICKER_GUARD_WINDOW_MS
                            }
                        ) {
                            MainScreen(
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                themeMode = themeMode,
                                onChangeThemeMode = { newMode ->
                                    viewModel.setThemeMode(newMode)
                                },
                                onToggleDarkTheme = {
                                    val nextMode = if (isDarkTheme) "LIGHT" else "DARK"
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
            viewModel.queueIncomingImportUris(safeUris)
        }
    }
}
