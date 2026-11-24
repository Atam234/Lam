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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardBackspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.argus.logger.FlushedLogEntry
import com.example.argus.logger.KeyboardLayoutTranslator
import com.example.argus.logger.LoggerMode
import com.example.argus.logger.LoggerSessionState
import com.example.argus.logger.SpecialKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLoggerScreen(
    state: LoggerSessionState,
    sandboxText: String,
    onModeChange: (LoggerMode) -> Unit,
    onEncryptionChange: (Boolean) -> Unit,
    onWindowNameChange: (String) -> Unit,
    onInitialLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onClipboardMonitoringChange: (Boolean) -> Unit,
    onToggleShift: () -> Unit,
    onToggleCapsLock: () -> Unit,
    onToggleCtrl: () -> Unit,
    onSandboxTextChange: (String, String?) -> Unit,
    onSpecialKey: (SpecialKey, String?) -> Unit,
    onFlushBuffer: () -> Unit,
    onClearFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val readClipboardText: () -> String? = {
        if (state.clipboardMonitoringEnabled) {
            clipboardManager.getText()?.text
        } else {
            null
        }
    }

    var showConfigPanel by remember { mutableStateOf(true) }
    var customWindowInput by remember(state.activeWindowName) { mutableStateOf(state.activeWindowName) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Telemetry Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ARGUS SESSION LOGGER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Interactive Keystroke & Crypto Telemetry",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            color = if (state.encryptionEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.encryptionEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (state.encryptionEnabled) "RSA OAEP Encrypted" else "Plaintext Mode",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.encryptionEnabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                                Text(
                                    text = if (state.encryptionEnabled) "OAEP-SHA3" else "PLAIN",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.encryptionEnabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }
                    }

                    // Mode Switcher (local vs debug)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logger_mode_selector")
                    ) {
                        LoggerMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.mode == mode,
                                onClick = { onModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, LoggerMode.entries.size),
                                modifier = Modifier.testTag("mode_${mode.cliFlag}")
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Buffer Progress Meter
                    val progress = (state.currentBytes.toFloat() / state.charLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Buffer: ${state.currentBytes} / ${state.charLimit} bytes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Backspace guard @ ${state.backspaceBufferLen}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress > 0.85f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = state.encryptionEnabled,
                                onCheckedChange = onEncryptionChange,
                                modifier = Modifier.testTag("encryption_toggle")
                            )
                            Text(
                                text = "Encrypt on flush",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = onFlushBuffer,
                            enabled = state.lineBuffer.isNotEmpty(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("flush_buffer_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Flush buffer", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Flush")
                        }
                    }
                }
            }
        }

        // Layout, Context & Clipboard Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Layout, Window Context & Clipboard",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(
                            onClick = { showConfigPanel = !showConfigPanel },
                            modifier = Modifier.testTag("toggle_config_panel")
                        ) {
                            Icon(
                                imageVector = if (showConfigPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle configuration panel"
                            )
                        }
                    }

                    AnimatedVisibility(visible = showConfigPanel) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Window Context Tag
                            val presets = listOf("Argus Sandbox", "Browser", "Terminal", "Notes")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.forEach { preset ->
                                    FilterChip(
                                        selected = state.activeWindowName == preset,
                                        onClick = {
                                            customWindowInput = preset
                                            onWindowNameChange(preset)
                                        },
                                        label = { Text(preset) },
                                        modifier = Modifier.testTag("window_preset_$preset")
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customWindowInput,
                                    onValueChange = { customWindowInput = it },
                                    label = { Text("Active [WindowName] Tag") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("window_name_input")
                                )
                                OutlinedButton(
                                    onClick = { onWindowNameChange(customWindowInput) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("apply_window_name_button")
                                ) {
                                    Text("Set")
                                }
                            }

                            // Initial vs Target Layout Dropdowns (Latin <-> Cyrillic)
                            LayoutDropdown(
                                label = "Initial Keymap",
                                selectedLanguage = state.initialLanguage,
                                onSelect = onInitialLanguageChange,
                                testTag = "initial_layout_dropdown"
                            )
                            LayoutDropdown(
                                label = "Active / Target Layout (Latin <-> Cyrillic)",
                                selectedLanguage = state.targetLanguage,
                                onSelect = onTargetLanguageChange,
                                testTag = "target_layout_dropdown"
                            )

                            // Opt-in Clipboard Monitor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text("Clipboard Tagging", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Logs [Clipboard: ...] when content changes (<300 chars)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.clipboardMonitoringEnabled,
                                    onCheckedChange = onClipboardMonitoringChange,
                                    modifier = Modifier.testTag("clipboard_monitoring_toggle")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interactive Capture Pad & Special Keys
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Session Input Pad", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = if (state.upperCase) "UPPER" else "lower",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Modifier chips (Shift, CapsLock, Ctrl)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.shiftOn,
                            onClick = onToggleShift,
                            label = { Text("Shift") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("modifier_shift")
                        )
                        FilterChip(
                            selected = state.capsLockOn,
                            onClick = onToggleCapsLock,
                            label = { Text("CapsLock") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("modifier_capslock")
                        )
                        FilterChip(
                            selected = state.ctrlOn,
                            onClick = onToggleCtrl,
                            label = { Text("Ctrl") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("modifier_ctrl")
                        )
                    }

                    OutlinedTextField(
                        value = sandboxText,
                        onValueChange = { newValue ->
                            onSandboxTextChange(newValue, readClipboardText())
                        },
                        label = { Text("Type here to capture & translate keystrokes") },
                        placeholder = { Text("Supports IME & hardware keyboard events...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_input_pad")
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (event.isCtrlPressed || event.isShiftPressed) {
                                                onSpecialKey(SpecialKey.LEFT, readClipboardText())
                                                true
                                            } else {
                                                onSpecialKey(SpecialKey.LEFT, readClipboardText())
                                                true
                                            }
                                        }
                                        Key.DirectionRight -> {
                                            onSpecialKey(SpecialKey.RIGHT, readClipboardText())
                                            true
                                        }
                                        Key.Tab -> {
                                            onSpecialKey(SpecialKey.TAB, readClipboardText())
                                            true
                                        }
                                        Key.CapsLock -> {
                                            onToggleCapsLock()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    )

                    // Tactile Special Keys Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.TAB, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_tab")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardTab, contentDescription = "Tab key", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("TAB")
                        }
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.ENTER, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_enter")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Enter key", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ENTER")
                        }
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.BACKSPACE, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_backspace")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardBackspace, contentDescription = "Backspace key", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("BKSP")
                        }
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.LEFT, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_left")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left arrow key", modifier = Modifier.size(18.dp))
                        }
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.RIGHT, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_right")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right arrow key", modifier = Modifier.size(18.dp))
                        }
                        OutlinedButton(
                            onClick = { onSpecialKey(SpecialKey.SPACE, readClipboardText()) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("key_space")
                        ) {
                            Icon(Icons.Default.SpaceBar, contentDescription = "Space key", modifier = Modifier.size(16.dp))
                        }
                    }

                    // Active Line Buffer Preview
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small
                            )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "ACTIVE LINE_BUFFER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.lineBuffer.ifEmpty { "<empty buffer — type to record>" },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.lineBuffer.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }

                    state.lastError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Flushed Telemetry Stream
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Flushed Output Stream (${state.flushedFeed.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (state.flushedFeed.isNotEmpty()) {
                    TextButton(
                        onClick = onClearFeed,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("clear_feed_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (state.flushedFeed.isEmpty()) {
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
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No flushed blocks yet. Press ENTER, TAB, Flush, or reach ${state.charLimit} bytes to emit an encrypted/debug log chunk.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.flushedFeed, key = { it.id }) { entry ->
                FlushedEntryCard(entry = entry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDropdown(
    label: String,
    selectedLanguage: String,
    onSelect: (String) -> Unit,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLanguage,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag(testTag)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            KeyboardLayoutTranslator.supportedLayouts.forEach { (lcid, name) ->
                val isCyrillic = name in KeyboardLayoutTranslator.cyrillicLayouts
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (isCyrillic) "$lcid · CYR" else lcid,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCyrillic) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FlushedEntryCard(entry: FlushedLogEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (entry.isEncrypted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (entry.isEncrypted) "RSA-OAEP" else "PLAINTEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.isEncrypted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "${entry.mode.cliFlag.uppercase()} · ${entry.byteCount}B",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.payload.trim(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
