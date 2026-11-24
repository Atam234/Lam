package com.example.argus.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.argus.model.AiProviderProfile
import com.example.argus.model.ProviderProtocol
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    providers: List<AiProviderProfile>,
    activeProviderId: String,
    testingProviderId: String?,
    testStatusMessage: Pair<String, Boolean>?,
    onActivateProvider: (String) -> Unit,
    onUpdateProvider: (AiProviderProfile) -> Unit,
    onAddCustomProvider: (name: String, protocol: ProviderProtocol, baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onDeleteCustomProvider: (String) -> Unit,
    onTestConnection: (AiProviderProfile) -> Unit,
    onDismissTestStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customProtocol by remember { mutableStateOf(ProviderProtocol.OPENAI_COMPATIBLE) }
    var customBaseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var customApiKey by remember { mutableStateOf("") }
    var customModelId by remember { mutableStateOf("") }

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
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "UNIVERSAL AI API ROUTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "API Providers & Any AI Model",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Use Built-in Gemini with zero API key, or connect OpenAI, OpenRouter, Claude, Groq, DeepSeek, Ollama, or any custom AI endpoint and model ID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("add_custom_provider_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Custom AI API / Endpoint")
                    }
                }
            }
        }

        // Connection Test Status Banner
        testStatusMessage?.let { (message, isSuccess) ->
            item {
                Surface(
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissTestStatus) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // Providers List
        items(providers, key = { it.id }) { profile ->
            ProviderProfileCard(
                profile = profile,
                isActive = profile.id == activeProviderId,
                isTesting = testingProviderId == profile.id,
                onActivate = { onActivateProvider(profile.id) },
                onSave = onUpdateProvider,
                onTest = { onTestConnection(it) },
                onDelete = if (profile.isCustom) {
                    { onDeleteCustomProvider(profile.id) }
                } else {
                    null
                }
            )
        }
    }

    if (showAddDialog) {
        var protocolExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom AI API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Provider Name") },
                        placeholder = { Text("e.g., Together AI / Custom Server") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_provider_name_input")
                    )

                    ExposedDropdownMenuBox(
                        expanded = protocolExpanded,
                        onExpandedChange = { protocolExpanded = !protocolExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customProtocol.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("API Protocol") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = protocolExpanded,
                            onDismissRequest = { protocolExpanded = false }
                        ) {
                            listOf(
                                ProviderProtocol.OPENAI_COMPATIBLE,
                                ProviderProtocol.ANTHROPIC,
                                ProviderProtocol.GEMINI_REST
                            ).forEach { proto ->
                                DropdownMenuItem(
                                    text = { Text(proto.label) },
                                    onClick = {
                                        customProtocol = proto
                                        customBaseUrl = when (proto) {
                                            ProviderProtocol.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
                                            ProviderProtocol.ANTHROPIC -> "https://api.anthropic.com"
                                            ProviderProtocol.GEMINI_REST -> "https://generativelanguage.googleapis.com"
                                            ProviderProtocol.FIREBASE_GEMINI -> ""
                                        }
                                        protocolExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_provider_url_input")
                    )

                    OutlinedTextField(
                        value = customModelId,
                        onValueChange = { customModelId = it },
                        label = { Text("Model ID") },
                        placeholder = { Text("e.g., meta-llama/Llama-3.3-70B-Instruct") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_provider_model_input")
                    )

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key (stored locally on device)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_provider_key_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomProvider(
                            customName,
                            customProtocol,
                            customBaseUrl,
                            customApiKey,
                            customModelId
                        )
                        customName = ""
                        customApiKey = ""
                        customModelId = ""
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_provider_button")
                ) {
                    Text("Save & Use")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProviderProfileCard(
    profile: AiProviderProfile,
    isActive: Boolean,
    isTesting: Boolean,
    onActivate: () -> Unit,
    onSave: (AiProviderProfile) -> Unit,
    onTest: (AiProviderProfile) -> Unit,
    onDelete: (() -> Unit)?
) {
    var expanded by remember(isActive) { mutableStateOf(isActive) }
    var modelInput by remember(profile.id, profile.modelId) { mutableStateOf(profile.modelId) }
    var baseUrlInput by remember(profile.id, profile.baseUrl) { mutableStateOf(profile.baseUrl) }
    var apiKeyInput by remember(profile.id, profile.apiKey) { mutableStateOf(profile.apiKey) }
    var temperatureInput by remember(profile.id, profile.temperature) { mutableFloatStateOf(profile.temperature) }
    var showApiKey by remember { mutableStateOf(false) }

    val draftProfile = profile.copy(
        modelId = modelInput.trim().ifEmpty { profile.modelId },
        baseUrl = baseUrlInput.trim(),
        apiKey = apiKeyInput.trim(),
        temperature = temperatureInput
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = if (isActive) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        } else {
            Modifier
        }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active provider",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "${profile.protocol.label} · ${profile.modelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isActive) {
                        OutlinedButton(
                            onClick = {
                                onSave(draftProfile)
                                onActivate()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("use_provider_${profile.id}")
                        ) {
                            Text("Use")
                        }
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("expand_provider_${profile.id}")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle provider settings"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profile.presetModels.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            profile.presetModels.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        modelInput = preset
                                        onSave(draftProfile.copy(modelId = preset))
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model ID (type any model)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_input_${profile.id}")
                    )

                    if (profile.protocol != ProviderProtocol.FIREBASE_GEMINI) {
                        OutlinedTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_${profile.id}")
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API Key (saved locally on device)") },
                            placeholder = { Text("Paste your API key here") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle API key visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("key_input_${profile.id}")
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Uses Firebase AI Logic with App Check attestation — no API key required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Temperature: ${(temperatureInput * 10f).roundToInt() / 10f}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = temperatureInput,
                            onValueChange = { temperatureInput = it },
                            valueRange = 0f..1.5f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onSave(draftProfile)
                                onActivate()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_provider_${profile.id}")
                        ) {
                            Text("Save & Activate")
                        }
                        OutlinedButton(
                            onClick = {
                                onSave(draftProfile)
                                onTest(draftProfile)
                            },
                            enabled = !isTesting,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("test_provider_${profile.id}")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = "Test API connection", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test")
                            }
                        }
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_provider_${profile.id}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete custom provider",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
