package com.example.argus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.argus.ui.ArgusTab
import com.example.argus.ui.ArgusViewModel
import com.example.argus.ui.screens.LogDecryptorScreen
import com.example.argus.ui.screens.LogVaultScreen
import com.example.argus.ui.screens.RsaKeysScreen
import com.example.argus.ui.screens.SessionLoggerScreen
import com.example.argus.ui.theme.ArgusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArgusTheme(darkTheme = true) {
                ArgusRootApp()
            }
        }
    }
}

private data class NavItemSpec(
    val tab: ArgusTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArgusRootApp(
    viewModel: ArgusViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val sandboxText by viewModel.sandboxText.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val decryptorState by viewModel.decryptorState.collectAsStateWithLifecycle()
    val keysState by viewModel.keysState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    if (selectedTab != ArgusTab.LOGGER) {
        BackHandler {
            viewModel.selectTab(ArgusTab.LOGGER)
        }
    }

    val navItems = remember {
        listOf(
            NavItemSpec(
                tab = ArgusTab.LOGGER,
                labelRes = R.string.nav_logger,
                selectedIcon = Icons.Filled.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
                testTag = "nav_tab_logger"
            ),
            NavItemSpec(
                tab = ArgusTab.VAULT,
                labelRes = R.string.nav_vault,
                selectedIcon = Icons.Filled.Folder,
                unselectedIcon = Icons.Outlined.Folder,
                testTag = "nav_tab_vault"
            ),
            NavItemSpec(
                tab = ArgusTab.DECRYPTOR,
                labelRes = R.string.nav_decryptor,
                selectedIcon = Icons.Filled.LockOpen,
                unselectedIcon = Icons.Outlined.LockOpen,
                testTag = "nav_tab_decryptor"
            ),
            NavItemSpec(
                tab = ArgusTab.KEYS,
                labelRes = R.string.nav_keys,
                selectedIcon = Icons.Filled.Key,
                unselectedIcon = Icons.Outlined.Key,
                testTag = "nav_tab_keys"
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val isSelected = selectedTab == item.tab
                            val label = stringResource(item.labelRes)
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets(0))
                    ) {
                        Spacer(Modifier.weight(0.1f))
                        navItems.forEach { item ->
                            val isSelected = selectedTab == item.tab
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp)) {
                        when (selectedTab) {
                            ArgusTab.LOGGER -> SessionLoggerScreen(
                                state = sessionState,
                                sandboxText = sandboxText,
                                onModeChange = viewModel::setLoggerMode,
                                onEncryptionChange = viewModel::setEncryptionEnabled,
                                onWindowNameChange = viewModel::setActiveWindowName,
                                onInitialLanguageChange = viewModel::setInitialLanguage,
                                onTargetLanguageChange = viewModel::setTargetLanguage,
                                onClipboardMonitoringChange = viewModel::setClipboardMonitoring,
                                onToggleShift = viewModel::toggleShift,
                                onToggleCapsLock = viewModel::toggleCapsLock,
                                onToggleCtrl = viewModel::toggleCtrl,
                                onSandboxTextChange = viewModel::onSandboxTextChanged,
                                onSpecialKey = viewModel::onSpecialKeyPressed,
                                onFlushBuffer = viewModel::flushBuffer,
                                onClearFeed = viewModel::clearSessionFeed
                            )
                            ArgusTab.VAULT -> LogVaultScreen(
                                state = vaultState,
                                onRefresh = viewModel::refreshVaultLogs,
                                onInspectLog = viewModel::inspectVaultLog,
                                onDismissPreview = viewModel::dismissVaultPreview,
                                onSendToDecryptor = viewModel::sendVaultLogToDecryptor,
                                onDeleteLog = viewModel::deleteVaultLog,
                                onUploadEndpointChange = viewModel::setUploadEndpointUrl,
                                onUploadLog = viewModel::uploadVaultLog
                            )
                            ArgusTab.DECRYPTOR -> LogDecryptorScreen(
                                state = decryptorState,
                                availableKeys = keysState.keyPairs,
                                onEncryptedTextChange = viewModel::updateDecryptEncryptedInput,
                                onPrivateKeyChange = viewModel::updateDecryptPrivateKey,
                                onSelectStoredKey = viewModel::selectStoredKeyForDecryption,
                                onPassphraseChange = viewModel::updateDecryptPassphrase,
                                onDecryptClick = viewModel::decryptLog,
                                onClearOutputClick = viewModel::clearDecryptionOutput
                            )
                            ArgusTab.KEYS -> RsaKeysScreen(
                                state = keysState,
                                onGenerateKeyPair = viewModel::generateNewKeyPair,
                                onActivateKeyPair = viewModel::setActiveKeyPair,
                                onImportPublicKeyPem = viewModel::importPublicKeyPem,
                                onDeleteKeyPair = viewModel::deleteKeyPair
                            )
                        }
                    }
                }
            }
        }
    }
}
