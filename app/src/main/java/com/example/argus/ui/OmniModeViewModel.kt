package com.example.argus.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.argus.ai.MultiModelAiEngine
import com.example.argus.data.OmniModeRepository
import com.example.argus.model.AiModePreset
import com.example.argus.model.AiProviderProfile
import com.example.argus.model.ChatMessage
import com.example.argus.model.MessageRole
import com.example.argus.model.ProviderProtocol
import com.example.argus.model.RunSessionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val TAG = "OmniModeViewModel"

enum class OmniTab {
    MODES, RUN, MODELS, HISTORY
}

sealed interface InferenceUiState {
    data object Idle : InferenceUiState
    data object Loading : InferenceUiState
    data class Streaming(val partialText: String) : InferenceUiState
    data class Success(val text: String) : InferenceUiState
    data class Error(val message: String) : InferenceUiState
}

data class OmniModeUiState(
    val selectedTab: OmniTab = OmniTab.MODES,
    val providers: List<AiProviderProfile> = emptyList(),
    val activeProviderId: String = "firebase_gemini",
    val modes: List<AiModePreset> = emptyList(),
    val activeModeId: String = "mode_assistant",
    val activeSessionId: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val inferenceState: InferenceUiState = InferenceUiState.Idle,
    val history: List<RunSessionRecord> = emptyList(),
    val testingProviderId: String? = null,
    val testStatusMessage: Pair<String, Boolean>? = null, // message to isSuccess
    val snackbarMessage: String? = null
) {
    val activeProvider: AiProviderProfile?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val activeMode: AiModePreset?
        get() = modes.firstOrNull { it.id == activeModeId } ?: modes.firstOrNull()
}

class OmniModeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OmniModeRepository(application.applicationContext)
    private val aiEngine = MultiModelAiEngine()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")

    private val _uiState = MutableStateFlow(OmniModeUiState())
    val uiState: StateFlow<OmniModeUiState> = _uiState.asStateFlow()

    private var activeStreamingJob: Job? = null

    init {
        loadWorkspace()
    }

    private fun loadWorkspace() {
        viewModelScope.launch {
            val providers = repository.loadProviders()
            val modes = repository.loadModes()
            val history = repository.loadHistory()
            val savedProviderId = repository.getActiveProviderId()
                ?.takeIf { id -> providers.any { it.id == id } }
                ?: providers.firstOrNull()?.id
                ?: "firebase_gemini"
            val savedModeId = repository.getActiveModeId()
                ?.takeIf { id -> modes.any { it.id == id } }
                ?: modes.firstOrNull()?.id
                ?: "mode_assistant"

            _uiState.update {
                it.copy(
                    providers = providers,
                    activeProviderId = savedProviderId,
                    modes = modes,
                    activeModeId = savedModeId,
                    history = history
                )
            }
        }
    }

    fun selectTab(tab: OmniTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearTestStatus() {
        _uiState.update { it.copy(testStatusMessage = null) }
    }

    // --- MODE SELECTION & CUSTOM MODES ---

    fun selectModeAndLaunch(mode: AiModePreset) {
        _uiState.update {
            it.copy(
                activeModeId = mode.id,
                selectedTab = OmniTab.RUN
            )
        }
        viewModelScope.launch {
            repository.setActiveModeId(mode.id)
        }
    }

    fun selectModeInRunner(mode: AiModePreset) {
        _uiState.update { it.copy(activeModeId = mode.id) }
        viewModelScope.launch {
            repository.setActiveModeId(mode.id)
        }
    }

    fun createCustomMode(name: String, subtitle: String, systemPrompt: String) {
        val cleanName = name.trim().ifEmpty { "Custom Mode" }
        val cleanPrompt = systemPrompt.trim().ifEmpty { "You are a helpful AI assistant." }
        val newMode = AiModePreset(
            id = "custom_${UUID.randomUUID()}",
            name = cleanName,
            subtitle = subtitle.trim().ifEmpty { "Custom system prompt workflow" },
            systemPrompt = cleanPrompt,
            starterPrompts = listOf("Start with this custom mode"),
            iconKey = "tune",
            isCustom = true
        )
        val updated = listOf(newMode) + _uiState.value.modes
        _uiState.update {
            it.copy(
                modes = updated,
                activeModeId = newMode.id,
                selectedTab = OmniTab.RUN,
                snackbarMessage = "Created mode: ${newMode.name}"
            )
        }
        viewModelScope.launch {
            repository.saveModes(updated)
            repository.setActiveModeId(newMode.id)
        }
    }

    fun deleteCustomMode(modeId: String) {
        val updated = _uiState.value.modes.filterNot { it.id == modeId && it.isCustom }
        val nextActive = if (_uiState.value.activeModeId == modeId) {
            updated.firstOrNull()?.id ?: "mode_assistant"
        } else {
            _uiState.value.activeModeId
        }
        _uiState.update {
            it.copy(
                modes = updated,
                activeModeId = nextActive,
                snackbarMessage = "Deleted custom mode"
            )
        }
        viewModelScope.launch {
            repository.saveModes(updated)
            repository.setActiveModeId(nextActive)
        }
    }

    // --- PROVIDERS & MODELS ---

    fun activateProvider(providerId: String) {
        _uiState.update {
            val name = it.providers.firstOrNull { p -> p.id == providerId }?.name ?: "Provider"
            it.copy(
                activeProviderId = providerId,
                snackbarMessage = "Active AI provider: $name"
            )
        }
        viewModelScope.launch {
            repository.setActiveProviderId(providerId)
        }
    }

    fun updateProviderConfig(updated: AiProviderProfile) {
        val list = _uiState.value.providers.map { if (it.id == updated.id) updated else it }
        _uiState.update { it.copy(providers = list) }
        viewModelScope.launch {
            repository.saveProviders(list)
        }
    }

    fun switchActiveModelId(modelId: String) {
        val active = _uiState.value.activeProvider ?: return
        val updated = active.copy(modelId = modelId.trim())
        updateProviderConfig(updated)
        _uiState.update { it.copy(snackbarMessage = "Model set to ${updated.modelId}") }
    }

    fun addCustomProvider(
        name: String,
        protocol: ProviderProtocol,
        baseUrl: String,
        apiKey: String,
        modelId: String
    ) {
        val cleanName = name.trim().ifEmpty { "Custom AI API" }
        val cleanModel = modelId.trim().ifEmpty { "gpt-4o-mini" }
        val newProfile = AiProviderProfile(
            id = "provider_${UUID.randomUUID()}",
            name = cleanName,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            modelId = cleanModel,
            presetModels = listOf(cleanModel),
            isCustom = true
        )
        val updated = _uiState.value.providers + newProfile
        _uiState.update {
            it.copy(
                providers = updated,
                activeProviderId = newProfile.id,
                snackbarMessage = "Added & activated $cleanName"
            )
        }
        viewModelScope.launch {
            repository.saveProviders(updated)
            repository.setActiveProviderId(newProfile.id)
        }
    }

    fun deleteCustomProvider(providerId: String) {
        val updated = _uiState.value.providers.filterNot { it.id == providerId && it.isCustom }
        val nextActive = if (_uiState.value.activeProviderId == providerId) {
            updated.firstOrNull()?.id ?: "firebase_gemini"
        } else {
            _uiState.value.activeProviderId
        }
        _uiState.update {
            it.copy(
                providers = updated,
                activeProviderId = nextActive,
                snackbarMessage = "Removed custom provider"
            )
        }
        viewModelScope.launch {
            repository.saveProviders(updated)
            repository.setActiveProviderId(nextActive)
        }
    }

    fun testProviderConnection(provider: AiProviderProfile) {
        if (provider.protocol.requiresKey &&
            provider.apiKey.isBlank() &&
            !provider.baseUrl.contains("10.0.2.2") &&
            !provider.baseUrl.contains("localhost") &&
            !provider.baseUrl.contains("127.0.0.1")
        ) {
            _uiState.update {
                it.copy(
                    testStatusMessage = "Enter an API key for ${provider.name} first." to false
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testingProviderId = provider.id, testStatusMessage = null) }
            val result = aiEngine.testConnection(provider)
            result.onSuccess { reply ->
                _uiState.update {
                    it.copy(
                        testingProviderId = null,
                        testStatusMessage = "${provider.name} (${provider.modelId}) OK: ${reply.take(120)}" to true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        testingProviderId = null,
                        testStatusMessage = "${provider.name} failed: ${err.message ?: "Unknown error"}" to false
                    )
                }
            }
        }
    }

    // --- RUNNER / INFERENCE ---

    fun runPrompt(promptText: String) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return
        val stateSnapshot = _uiState.value
        val provider = stateSnapshot.activeProvider ?: return
        val mode = stateSnapshot.activeMode ?: return

        if (provider.protocol.requiresKey &&
            provider.apiKey.isBlank() &&
            !provider.baseUrl.contains("10.0.2.2") &&
            !provider.baseUrl.contains("localhost") &&
            !provider.baseUrl.contains("127.0.0.1")
        ) {
            _uiState.update {
                it.copy(
                    inferenceState = InferenceUiState.Error(
                        "${provider.name} needs an API key. Open 'API & Models' to enter your key, or switch to Built-in Gemini (no key needed)."
                    )
                )
            }
            return
        }

        activeStreamingJob?.cancel()

        val priorHistory = stateSnapshot.messages.filter {
            it.role == MessageRole.USER || it.role == MessageRole.MODEL
        }
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = cleanPrompt,
            timestamp = LocalDateTime.now().format(timeFormatter)
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inferenceState = InferenceUiState.Loading
            )
        }

        activeStreamingJob = viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                aiEngine.streamCompletion(
                    provider = provider,
                    systemPrompt = mode.systemPrompt,
                    history = priorHistory,
                    userPrompt = cleanPrompt
                ).collect { token ->
                    accumulated.append(token)
                    _uiState.update {
                        it.copy(inferenceState = InferenceUiState.Streaming(accumulated.toString()))
                    }
                }

                val finalResponse = accumulated.toString().trim()
                if (finalResponse.isNotEmpty()) {
                    val modelMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.MODEL,
                        text = finalResponse,
                        timestamp = LocalDateTime.now().format(timeFormatter),
                        modelBadge = "${provider.name} · ${provider.modelId}"
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + modelMsg,
                            inferenceState = InferenceUiState.Success(finalResponse)
                        )
                    }
                    persistCurrentSession()
                } else {
                    _uiState.update {
                        it.copy(
                            inferenceState = InferenceUiState.Error("Model returned an empty response.")
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Inference timed out", e)
                _uiState.update {
                    it.copy(
                        inferenceState = InferenceUiState.Error("Request timed out after 60 seconds. Please try again.")
                    )
                }
            } catch (e: CancellationException) {
                if (accumulated.isNotBlank()) {
                    val partialMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.MODEL,
                        text = accumulated.toString().trim() + "\n\n[Stopped]",
                        timestamp = LocalDateTime.now().format(timeFormatter),
                        modelBadge = "${provider.name} · ${provider.modelId}"
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + partialMsg,
                            inferenceState = InferenceUiState.Idle
                        )
                    }
                } else {
                    _uiState.update { it.copy(inferenceState = InferenceUiState.Idle) }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI inference failed", e)
                val errMsg = e.localizedMessage ?: "AI API request failed."
                _uiState.update {
                    it.copy(inferenceState = InferenceUiState.Error(errMsg))
                }
            }
        }
    }

    fun stopStreaming() {
        activeStreamingJob?.cancel()
        activeStreamingJob = null
    }

    fun startNewSession() {
        activeStreamingJob?.cancel()
        _uiState.update {
            it.copy(
                activeSessionId = UUID.randomUUID().toString(),
                messages = emptyList(),
                inferenceState = InferenceUiState.Idle
            )
        }
    }

    private fun persistCurrentSession() {
        val s = _uiState.value
        val provider = s.activeProvider ?: return
        val mode = s.activeMode ?: return
        if (s.messages.isEmpty()) return

        val title = s.messages.firstOrNull { it.role == MessageRole.USER }?.text
            ?.replace("\n", " ")
            ?.take(48)
            ?.ifBlank { mode.name }
            ?: mode.name

        val record = RunSessionRecord(
            id = s.activeSessionId,
            title = title,
            modeId = mode.id,
            modeName = mode.name,
            providerName = provider.name,
            modelId = provider.modelId,
            updatedAt = LocalDateTime.now().format(dateTimeFormatter),
            messages = s.messages
        )
        val updatedHistory = listOf(record) + s.history.filterNot { it.id == record.id }
        _uiState.update { it.copy(history = updatedHistory) }
        viewModelScope.launch {
            repository.saveHistory(updatedHistory)
        }
    }

    // --- HISTORY ---

    fun reopenHistorySession(record: RunSessionRecord) {
        activeStreamingJob?.cancel()
        val matchedMode = _uiState.value.modes.firstOrNull { it.id == record.modeId }?.id
            ?: _uiState.value.activeModeId
        _uiState.update {
            it.copy(
                activeSessionId = record.id,
                activeModeId = matchedMode,
                messages = record.messages,
                inferenceState = InferenceUiState.Idle,
                selectedTab = OmniTab.RUN
            )
        }
    }

    fun deleteHistorySession(sessionId: String) {
        val updated = _uiState.value.history.filterNot { it.id == sessionId }
        _uiState.update { it.copy(history = updated, snackbarMessage = "Deleted session") }
        viewModelScope.launch {
            repository.saveHistory(updated)
        }
    }
}
