package com.example.argus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.argus.model.AiModePreset
import com.example.argus.model.AiProviderProfile
import com.example.argus.model.ChatMessage
import com.example.argus.model.MessageRole
import com.example.argus.ui.InferenceUiState

@Composable
fun RunnerScreen(
    activeMode: AiModePreset?,
    activeProvider: AiProviderProfile?,
    allModes: List<AiModePreset>,
    allProviders: List<AiProviderProfile>,
    messages: List<ChatMessage>,
    inferenceState: InferenceUiState,
    onRunPrompt: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onNewSession: () -> Unit,
    onSwitchMode: (AiModePreset) -> Unit,
    onSwitchProvider: (String) -> Unit,
    onSwitchModelId: (String) -> Unit,
    onOpenModelsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var promptInput by remember { mutableStateOf("") }
    var showModelSwitcher by remember { mutableStateOf(false) }
    var showModeSwitcher by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isBusy = inferenceState is InferenceUiState.Loading || inferenceState is InferenceUiState.Streaming
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val hasExtraStatusItem = inferenceState is InferenceUiState.Loading ||
        inferenceState is InferenceUiState.Streaming ||
        inferenceState is InferenceUiState.Error

    LaunchedEffect(messages.size, inferenceState, imeBottomPx) {
        val totalItems = messages.size + (if (hasExtraStatusItem) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Active Mode & Model Control Strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showModeSwitcher = true }
                        .testTag("runner_mode_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Switch AI Mode",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = activeMode?.name ?: "Mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showModelSwitcher = true }
                        .testTag("runner_model_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = "Switch AI Model",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = activeProvider?.modelId ?: "gemini-3.8-flash",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onNewSession,
                    modifier = Modifier.testTag("new_session_button")
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = "New session")
                }
            }
        }

        // Conversation / Inference Output Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && inferenceState is InferenceUiState.Idle) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = activeMode?.name ?: "AI Mode Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeMode?.subtitle ?: "Enter a prompt below to run this mode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val starters = activeMode?.starterPrompts.orEmpty()
                            if (starters.isNotEmpty()) {
                                Text(
                                    text = "Try a starter prompt:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                starters.forEachIndexed { idx, starter ->
                                    AssistChip(
                                        onClick = { onRunPrompt(starter) },
                                        label = {
                                            Text(
                                                text = starter,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("starter_prompt_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    onCopy = { clipboard.setText(AnnotatedString(msg.text)) }
                )
            }

            when (inferenceState) {
                is InferenceUiState.Loading -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Running ${activeProvider?.modelId ?: "AI model"}...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                is InferenceUiState.Streaming -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${activeProvider?.name ?: "Model"} · Streaming...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = inferenceState.partialText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                is InferenceUiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
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
                                        Icons.Default.ErrorOutline,
                                        contentDescription = "API error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = inferenceState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenModelsTab,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open API & Models")
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        // Prompt Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Mag-type ng prompt para sa ${activeMode?.name ?: "AI"}...") },
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("runner_prompt_input")
                )

                if (isBusy) {
                    FilledIconButton(
                        onClick = onStopStreaming,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("stop_streaming_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val toSend = promptInput
                            promptInput = ""
                            onRunPrompt(toSend)
                        },
                        enabled = promptInput.isNotBlank(),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("send_prompt_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run AI prompt")
                    }
                }
            }
        }
    }

    // Quick Mode Switcher Dialog
    if (showModeSwitcher) {
        AlertDialog(
            onDismissRequest = { showModeSwitcher = false },
            title = { Text("Switch AI Mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allModes.forEach { mode ->
                        Surface(
                            color = if (mode.id == activeMode?.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onSwitchMode(mode)
                                    showModeSwitcher = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(mode.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeSwitcher = false }) { Text("Close") }
            }
        )
    }

    // Quick Model & Provider Switcher Dialog
    if (showModelSwitcher) {
        var customModelText by remember(activeProvider?.modelId) {
            mutableStateOf(activeProvider?.modelId.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showModelSwitcher = false },
            title = { Text("Quick Switch Provider & Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allProviders.forEach { p ->
                            val selected = p.id == activeProvider?.id
                            Surface(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onSwitchProvider(p.id)
                                        customModelText = p.modelId
                                    }
                            ) {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    val presets = activeProvider?.presetModels.orEmpty()
                    if (presets.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        customModelText = preset
                                        onSwitchModelId(preset)
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customModelText,
                        onValueChange = { customModelText = it },
                        label = { Text("Any Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customModelText.isNotBlank()) {
                            onSwitchModelId(customModelText)
                        }
                        showModelSwitcher = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showModelSwitcher = false
                        onOpenModelsTab()
                    }
                ) {
                    Text("Configure Keys")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.96f)
                .border(
                    width = 1.dp,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You · ${message.timestamp}" else (message.modelBadge ?: "AI · ${message.timestamp}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isUser) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
