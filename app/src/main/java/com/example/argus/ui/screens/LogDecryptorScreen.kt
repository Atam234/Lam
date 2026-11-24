package com.example.argus.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.argus.data.StoredKeyPair
import com.example.argus.ui.DecryptorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LogDecryptorScreen(
    state: DecryptorUiState,
    availableKeys: List<StoredKeyPair>,
    onEncryptedTextChange: (String, String) -> Unit,
    onPrivateKeyChange: (String, String) -> Unit,
    onSelectStoredKey: (StoredKeyPair) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onDecryptClick: () -> Unit,
    onClearOutputClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showPassphrase by remember { mutableStateOf(false) }

    val openLogFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (content != null) {
                    onEncryptedTextChange(content, uri.lastPathSegment ?: "Selected Log File")
                }
            }
        }
    }

    val openPemKeyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (content != null) {
                    onPrivateKeyChange(content, uri.lastPathSegment ?: "Imported .pem")
                }
            }
        }
    }

    val saveDecryptedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val decrypted = state.result?.decryptedText
        if (uri != null && decrypted != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(decrypted.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "RSA-OAEP SHA3-512 DECRYPTOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Log Decryptor",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Decrypts ---START---...---END--- Base64 chunks using PKCS#1 OAEP (SHA3-512) and your PEM private key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Step 1: Encrypted Log Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. Encrypted Log Payload", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Source: ${state.encryptedSourceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedButton(
                            onClick = { openLogFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pick_encrypted_log_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Open encrypted log", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open File")
                        }
                    }

                    OutlinedTextField(
                        value = state.encryptedLogInput,
                        onValueChange = { onEncryptedTextChange(it, "Manual / Pasted") },
                        label = { Text("Encrypted ---START---...---END--- blocks") },
                        minLines = 4,
                        maxLines = 7,
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decrypt_log_input")
                    )
                }
            }
        }

        // Step 2: Private Key & Passphrase
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. Private Key (.pem)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Selected: ${state.privateKeySourceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedButton(
                            onClick = { openPemKeyLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pick_private_pem_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = "Load PEM file", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load .pem")
                        }
                    }

                    val decryptCapableKeys = availableKeys.filter { it.privateKeyPem.isNotBlank() }
                    if (decryptCapableKeys.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            decryptCapableKeys.forEach { pair ->
                                FilterChip(
                                    selected = state.privateKeyPemInput.trim() == pair.privateKeyPem.trim(),
                                    onClick = { onSelectStoredKey(pair) },
                                    label = { Text("${pair.label} (${pair.keySizeBits}b)") },
                                    modifier = Modifier.testTag("select_key_${pair.id}")
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.privateKeyPemInput,
                        onValueChange = { onPrivateKeyChange(it, "Manual / Pasted PEM") },
                        label = { Text("PEM Private Key") },
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decrypt_private_key_input")
                    )

                    OutlinedTextField(
                        value = state.passphrase,
                        onValueChange = onPassphraseChange,
                        label = { Text("Private Key Passphrase (optional)") },
                        singleLine = true,
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle passphrase visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decrypt_passphrase_input")
                    )

                    Button(
                        onClick = onDecryptClick,
                        enabled = !state.isDecrypting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("decrypt_log_button")
                    ) {
                        if (state.isDecrypting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Decrypting...")
                        } else {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Decrypt Log")
                        }
                    }

                    state.errorMessage?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Step 3: Decrypted Output
        state.result?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Decrypted Transcript", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = if (res.errorChunks == 0) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${res.successChunks}/${res.totalChunks} chunks OK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (res.errorChunks == 0) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        ) {
                            Text(
                                text = res.decryptedText.ifEmpty { "<empty output>" },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val baseName = state.encryptedSourceLabel
                                        .substringBeforeLast('.')
                                        .ifBlank { "log" }
                                    saveDecryptedLauncher.launch("${baseName}_decrypted.txt")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_decrypted_button")
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save .txt")
                            }
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(res.decryptedText)) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_decrypted_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy decrypted output", modifier = Modifier.size(16.dp))
                            }
                            OutlinedButton(
                                onClick = onClearOutputClick,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_decrypted_button")
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Clear output", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
