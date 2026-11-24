package com.example.argus.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.argus.data.VaultLogFile
import com.example.argus.ui.VaultUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogVaultScreen(
    state: VaultUiState,
    onRefresh: () -> Unit,
    onInspectLog: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onSendToDecryptor: (String) -> Unit,
    onDeleteLog: (String) -> Unit,
    onUploadEndpointChange: (String) -> Unit,
    onUploadLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportContent
        if (uri != null && content != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }
        pendingExportContent = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Vault Header
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MD5 DAILY LOG VAULT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Local Encrypted Storage",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("refresh_vault_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh logs")
                        }
                    }
                    Text(
                        text = "Today (${state.todaysDateLabel}) maps to:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.todaysMd5FileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Authorized Remote Upload Endpoint Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Optional Upload Endpoint", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Configure an authorized HTTP/HTTPS collector URL to manually POST an encrypted daily log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.uploadEndpointUrl,
                        onValueChange = onUploadEndpointChange,
                        label = { Text("Upload URL (https://...)") },
                        placeholder = { Text("https://collector.example.com/upload") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_endpoint_input")
                    )
                    if (state.isUploading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Uploading log & resolving public IP...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.networkResult?.let { diag ->
                        Surface(
                            color = if ((diag.uploadStatusCode ?: 0) in 200..299) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = diag.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if ((diag.uploadStatusCode ?: 0) in 200..299) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                diag.publicIp?.let { ip ->
                                    Text(
                                        text = "Observed Public IP: $ip",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stored Logs List
        if (state.logs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No daily logs saved yet. Switch to Logger in Local Vault mode and flush keystrokes to create today's MD5 log.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.logs, key = { it.fileName }) { logFile ->
                VaultLogCard(
                    logFile = logFile,
                    canUpload = state.uploadEndpointUrl.isNotBlank() && !state.isUploading,
                    onInspect = { onInspectLog(logFile.fileName) },
                    onDecrypt = { onSendToDecryptor(logFile.fileName) },
                    onExport = {
                        scope.launch {
                            val file = java.io.File(context.filesDir, "logs/${logFile.fileName}")
                            val text = withContext(Dispatchers.IO) {
                                if (file.exists()) file.readText(Charsets.UTF_8) else ""
                            }
                            pendingExportContent = text
                            exportLauncher.launch(logFile.fileName)
                        }
                    },
                    onUpload = { onUploadLog(logFile.fileName) },
                    onDelete = { onDeleteLog(logFile.fileName) }
                )
            }
        }
    }

    // Log Preview Modal
    if (state.selectedLogFileName != null && state.selectedLogContent != null) {
        AlertDialog(
            onDismissRequest = onDismissPreview,
            title = {
                Text(
                    text = state.selectedLogFileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = state.selectedLogContent.ifBlank { "<empty file>" },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = state.selectedLogFileName
                        onDismissPreview()
                        onSendToDecryptor(name)
                    }
                ) {
                    Text("Send to Decryptor")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun VaultLogCard(
    logFile: VaultLogFile,
    canUpload: Boolean,
    onInspect: () -> Unit,
    onDecrypt: () -> Unit,
    onExport: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = logFile.dateLabel ?: "Archived Log",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = logFile.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${logFile.chunkCount} chunks · ${logFile.sizeBytes}B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("inspect_${logFile.fileName}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Inspect", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View")
                }
                Button(
                    onClick = onDecrypt,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("decrypt_${logFile.fileName}")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Decrypt", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Decrypt")
                }
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.testTag("export_${logFile.fileName}")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export log file")
                }
                if (canUpload) {
                    IconButton(
                        onClick = onUpload,
                        modifier = Modifier.testTag("upload_${logFile.fileName}")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload log file")
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_${logFile.fileName}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete log file",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
