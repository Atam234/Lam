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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
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
import com.example.argus.ai.configureAppCheck
import com.example.argus.ui.OmniModeViewModel
import com.example.argus.ui.OmniTab
import com.example.argus.ui.screens.HistoryScreen
import com.example.argus.ui.screens.ModesScreen
import com.example.argus.ui.screens.ProvidersScreen
import com.example.argus.ui.screens.RunnerScreen
import com.example.argus.ui.theme.OmniModeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            OmniModeTheme(darkTheme = true) {
                OmniModeApp()
            }
        }
    }
}

private data class OmniNavSpec(
    val tab: OmniTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniModeApp(
    viewModel: OmniModeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    if (state.selectedTab != OmniTab.MODES) {
        BackHandler {
            viewModel.selectTab(OmniTab.MODES)
        }
    }

    val navItems = remember {
        listOf(
            OmniNavSpec(
                tab = OmniTab.MODES,
                labelRes = R.string.nav_modes,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_tab_modes"
            ),
            OmniNavSpec(
                tab = OmniTab.RUN,
                labelRes = R.string.nav_run,
                selectedIcon = Icons.Filled.PlayArrow,
                unselectedIcon = Icons.Outlined.PlayArrow,
                testTag = "nav_tab_run"
            ),
            OmniNavSpec(
                tab = OmniTab.MODELS,
                labelRes = R.string.nav_models,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_tab_models"
            ),
            OmniNavSpec(
                tab = OmniTab.HISTORY,
                labelRes = R.string.nav_history,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_tab_history"
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
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val isSelected = state.selectedTab == item.tab
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
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
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
                            val isSelected = state.selectedTab == item.tab
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
                    Box(modifier = Modifier.widthIn(max = 780.dp)) {
                        when (state.selectedTab) {
                            OmniTab.MODES -> ModesScreen(
                                modes = state.modes,
                                activeModeId = state.activeModeId,
                                activeProvider = state.activeProvider,
                                onSelectMode = viewModel::selectModeAndLaunch,
                                onCreateCustomMode = viewModel::createCustomMode,
                                onDeleteCustomMode = viewModel::deleteCustomMode,
                                onOpenModelsTab = { viewModel.selectTab(OmniTab.MODELS) }
                            )
                            OmniTab.RUN -> RunnerScreen(
                                activeMode = state.activeMode,
                                activeProvider = state.activeProvider,
                                allModes = state.modes,
                                allProviders = state.providers,
                                messages = state.messages,
                                inferenceState = state.inferenceState,
                                onRunPrompt = viewModel::runPrompt,
                                onStopStreaming = viewModel::stopStreaming,
                                onNewSession = viewModel::startNewSession,
                                onSwitchMode = viewModel::selectModeInRunner,
                                onSwitchProvider = viewModel::activateProvider,
                                onSwitchModelId = viewModel::switchActiveModelId,
                                onOpenModelsTab = { viewModel.selectTab(OmniTab.MODELS) }
                            )
                            OmniTab.MODELS -> ProvidersScreen(
                                providers = state.providers,
                                activeProviderId = state.activeProviderId,
                                testingProviderId = state.testingProviderId,
                                testStatusMessage = state.testStatusMessage,
                                onActivateProvider = viewModel::activateProvider,
                                onUpdateProvider = viewModel::updateProviderConfig,
                                onAddCustomProvider = viewModel::addCustomProvider,
                                onDeleteCustomProvider = viewModel::deleteCustomProvider,
                                onTestConnection = viewModel::testProviderConnection,
                                onDismissTestStatus = viewModel::clearTestStatus
                            )
                            OmniTab.HISTORY -> HistoryScreen(
                                history = state.history,
                                onReopenSession = viewModel::reopenHistorySession,
                                onDeleteSession = viewModel::deleteHistorySession
                            )
                        }
                    }
                }
            }
        }
    }
}
