package com.example.ui.tabs

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OnBrandTeal
import com.example.util.BackupCrypto
import com.example.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal const val MAX_BACKUP_FILE_BYTES: Int = 10 * 1024 * 1024

internal sealed interface ReadBackupFileResult {
    data class Success(val text: String) : ReadBackupFileResult
    data object TooLarge : ReadBackupFileResult
    data object ReadFailed : ReadBackupFileResult
}

internal sealed interface ParsedBackupPreview {
    data class Full(val payload: BackupManager.FullBackupPayload) : ParsedBackupPreview
    data class SettingsOnly(val restoredSettings: SettingsEntity) : ParsedBackupPreview
}

internal suspend fun readUriTextBounded(
    context: Context,
    uri: Uri,
    maxBytes: Int = MAX_BACKUP_FILE_BYTES
): ReadBackupFileResult = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    return@withContext ReadBackupFileResult.TooLarge
                }
                out.write(buffer, 0, read)
            }
            ReadBackupFileResult.Success(out.toString(Charsets.UTF_8.name()))
        } ?: ReadBackupFileResult.ReadFailed
    } catch (_: Exception) {
        ReadBackupFileResult.ReadFailed
    }
}

internal suspend fun writeUriTextUtf8(
    context: Context,
    uri: Uri,
    content: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

internal fun parseBackupJsonForPreview(
    rawJson: String,
    fallback: SettingsEntity
): ParsedBackupPreview? {
    val full = BackupManager.deserializeFullBackupFromJson(rawJson, fallback)
    if (full != null) return ParsedBackupPreview.Full(full)
    val settingsOnly = BackupManager.deserializeSettingsFromJson(rawJson, fallback)
    return if (settingsOnly != null) ParsedBackupPreview.SettingsOnly(settingsOnly) else null
}

@Composable
fun SettingsExportBackupDialog(
    onDismiss: () -> Unit,
    onConfirmExport: (fullBackupScope: Boolean, encryptEnabled: Boolean, passphrase: String) -> Unit
) {
    var fullBackupScope by remember { mutableStateOf(true) }
    var encryptEnabled by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    val passphraseValid = !encryptEnabled || (passphrase.length >= 8 && passphrase == confirmPassphrase)
    val validationHint = when {
        !encryptEnabled -> null
        passphrase.isEmpty() -> "Enter a passphrase (at least 8 characters)."
        passphrase.length < 8 -> "Passphrase must be at least 8 characters."
        confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase -> "Passphrases do not match."
        passphrase != confirmPassphrase -> "Confirm your passphrase."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Export Backup File",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Save portable JSON via system file picker",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Backup Scope",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Column(modifier = Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = fullBackupScope,
                                role = Role.RadioButton,
                                onClick = { fullBackupScope = true }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = fullBackupScope, onClick = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Full Backup", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Settings, ledger months, bank transactions, checklists & investment lots",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = !fullBackupScope,
                                role = Role.RadioButton,
                                onClick = { fullBackupScope = false }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !fullBackupScope, onClick = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Settings Only", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Salaries, expenses, tax rules & FIRE assumptions without transaction history",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { encryptEnabled = !encryptEnabled },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "Encrypt with AES-256-GCM",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Protects cloud & file copies with a passphrase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = encryptEnabled,
                        onCheckedChange = { encryptEnabled = it },
                        modifier = Modifier.testTag("switch_encrypt_backup")
                    )
                }

                if (encryptEnabled) {
                    val visual = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation()
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (min 8 characters)") },
                        singleLine = true,
                        visualTransformation = visual,
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassphrase = !showPassphrase },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_passphrase_input")
                    )
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text("Confirm Passphrase") },
                        singleLine = true,
                        visualTransformation = visual,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_confirm_passphrase_input")
                    )
                    if (validationHint != null) {
                        Text(
                            text = validationHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (passphrase.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = passphraseValid,
                onClick = { onConfirmExport(fullBackupScope, encryptEnabled, passphrase) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_export_backup_button")
            ) {
                Text("Choose Save Location", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SettingsPassphrasePromptDialog(
    envelopeJson: String,
    fallbackSettings: SettingsEntity,
    onDismiss: () -> Unit,
    onDecryptedPreview: (ParsedBackupPreview) -> Unit
) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isDecrypting) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Encrypted Backup Detected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Enter passphrase to decrypt and preview",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    label = { Text("Backup Passphrase") },
                    singleLine = true,
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassphrase = !showPassphrase },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("decrypt_passphrase_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isDecrypting && passphrase.isNotEmpty(),
                onClick = {
                    if (isDecrypting) return@Button
                    isDecrypting = true
                    val chars = passphrase.toCharArray()
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) {
                                try {
                                    BackupCrypto.decryptJson(envelopeJson, chars)
                                } finally {
                                    chars.fill('\u0000')
                                }
                            }
                            when (result) {
                                is BackupCrypto.DecryptResult.Success -> {
                                    val preview = withContext(Dispatchers.Default) {
                                        parseBackupJsonForPreview(result.plaintext, fallbackSettings)
                                    }
                                    if (preview != null) {
                                        passphrase = ""
                                        onDecryptedPreview(preview)
                                    } else {
                                        errorMessage = "Decrypted file is not a valid Martinu Financials backup."
                                    }
                                }
                                BackupCrypto.DecryptResult.WrongPassphraseOrTampered -> {
                                    errorMessage = "Wrong passphrase or modified backup file. Check your passphrase and try again."
                                }
                                BackupCrypto.DecryptResult.MalformedEnvelope -> {
                                    errorMessage = "Encrypted backup envelope is malformed or unsupported."
                                }
                            }
                        } finally {
                            isDecrypting = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (isDecrypting) "Decrypting..." else "Unlock & Preview", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isDecrypting,
                onClick = {
                    passphrase = ""
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SettingsBackupPreviewDialog(
    preview: ParsedBackupPreview,
    onDismiss: () -> Unit,
    onApplyPreview: (ParsedBackupPreview) -> Unit
) {
    val isFull = preview is ParsedBackupPreview.Full
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isFull) "Full Backup Ready" else "Settings-Only Backup Ready",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isFull) "Replaces local workspace data" else "Preserves ledger & bank history",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preview is ParsedBackupPreview.Full) {
                    val p = preview.payload
                    Text(
                        text = "Detected Full Backup:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "- Base year: ${p.settings.baseYear}\n" +
                            "- Ledger months: ${p.ledgerEntries.size}\n" +
                            "- Bank transactions: ${p.importedTransactions.size}\n" +
                            "- Action checklist states: ${p.actionStates.size}\n" +
                            "- Investment lots: ${p.investmentLots.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Restoring a full backup replaces all current local records and preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (preview is ParsedBackupPreview.SettingsOnly) {
                    Text(
                        text = "Detected Settings-Only Backup (base year ${preview.restoredSettings.baseYear}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Restoring updates your configuration parameters while keeping your existing monthly ledger, imported bank transactions, and investment lots intact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyPreview(preview) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_restore_preview_button")
            ) {
                Text(if (isFull) "Restore Full Backup" else "Apply Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsImportJsonDialog(
    settings: SettingsEntity,
    onDismiss: () -> Unit,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onRestoreFullBackup: ((BackupManager.FullBackupPayload) -> Unit)?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var importJsonText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var isParsing by remember { mutableStateOf(false) }
    var encryptedEnvelopeToUnlock by remember { mutableStateOf<String?>(null) }
    var previewToConfirm by remember { mutableStateOf<ParsedBackupPreview?>(null) }

    if (encryptedEnvelopeToUnlock != null) {
        SettingsPassphrasePromptDialog(
            envelopeJson = encryptedEnvelopeToUnlock!!,
            fallbackSettings = settings,
            onDismiss = { encryptedEnvelopeToUnlock = null },
            onDecryptedPreview = { preview ->
                encryptedEnvelopeToUnlock = null
                previewToConfirm = preview
            }
        )
    }

    if (previewToConfirm != null) {
        SettingsBackupPreviewDialog(
            preview = previewToConfirm!!,
            onDismiss = { previewToConfirm = null },
            onApplyPreview = { preview ->
                previewToConfirm = null
                when (preview) {
                    is ParsedBackupPreview.Full -> {
                        if (onRestoreFullBackup != null) {
                            onRestoreFullBackup(preview.payload)
                        } else {
                            onUpdateSettings(preview.payload.settings)
                        }
                        onDismiss()
                    }
                    is ParsedBackupPreview.SettingsOnly -> {
                        onUpdateSettings(preview.restoredSettings)
                        onDismiss()
                        Toast.makeText(context, "Settings successfully restored", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandTeal.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Paste JSON Backup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Supports settings-only, full, or encrypted JSON",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "For full backups we recommend Import Backup File above. You can also paste small or settings-only JSON below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = {
                        importJsonText = it
                        importErrorMessage = null
                    },
                    label = { Text("JSON Data") },
                    minLines = 5,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = BodyFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                if (importErrorMessage != null) {
                    Text(
                        text = importErrorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isParsing && importJsonText.isNotBlank(),
                onClick = {
                    if (isParsing) return@Button
                    isParsing = true
                    val rawJson = importJsonText
                    coroutineScope.launch {
                        try {
                            if (withContext(Dispatchers.Default) { BackupCrypto.isEncryptedBackup(rawJson) }) {
                                encryptedEnvelopeToUnlock = rawJson
                            } else {
                                val preview = withContext(Dispatchers.Default) {
                                    parseBackupJsonForPreview(rawJson, settings)
                                }
                                if (preview != null) {
                                    previewToConfirm = preview
                                } else {
                                    importErrorMessage = "Invalid JSON format. Please check the pasted data."
                                }
                            }
                        } finally {
                            isParsing = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = OnBrandTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Inspect & Restore", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsResetDialog(
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    val warningInk = if (LocalIsDarkTheme.current) AmberWarning else Color(0xFFB45309)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberWarning.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text("Reset Settings?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Restore defaults",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Text(
                text = "This will reset all your settings (like income, expenses, and FIRE target) back to their default values. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmReset()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarning,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Reset Defaults", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsClearDataDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Clear All User Data?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                    Text(
                        text = "Permanent deletion",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        text = {
            Text(
                text = "This will PERMANENTLY delete all your ledger entries, action plan checks, and reset all your settings. Are you absolutely sure?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmClear()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Delete Everything", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
