package com.example.argus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.argus.crypto.DecryptionResult
import com.example.argus.crypto.RsaCryptoEngine
import com.example.argus.data.ArgusSettings
import com.example.argus.data.LogVaultRepository
import com.example.argus.data.NetworkDiagnosticResult
import com.example.argus.data.StoredKeyPair
import com.example.argus.data.VaultLogFile
import com.example.argus.logger.KeystrokeLogEngine
import com.example.argus.logger.LoggerMode
import com.example.argus.logger.LoggerSessionState
import com.example.argus.logger.SpecialKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ArgusTab {
    LOGGER, VAULT, DECRYPTOR, KEYS
}

data class DecryptorUiState(
    val encryptedLogInput: String = "",
    val encryptedSourceLabel: String = "No file selected",
    val privateKeyPemInput: String = "",
    val privateKeySourceLabel: String = "No PEM key selected",
    val passphrase: String = "",
    val isDecrypting: Boolean = false,
    val result: DecryptionResult? = null,
    val errorMessage: String? = null
)

data class KeysUiState(
    val keyPairs: List<StoredKeyPair> = emptyList(),
    val activeKeyId: String? = null,
    val customPublicKeyPem: String? = null,
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class VaultUiState(
    val logs: List<VaultLogFile> = emptyList(),
    val todaysMd5FileName: String = "",
    val todaysDateLabel: String = "",
    val selectedLogFileName: String? = null,
    val selectedLogContent: String? = null,
    val uploadEndpointUrl: String = "",
    val isUploading: Boolean = false,
    val networkResult: NetworkDiagnosticResult? = null
)

class ArgusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LogVaultRepository(application.applicationContext)
    private val logEngine = KeystrokeLogEngine()

    private val _selectedTab = MutableStateFlow(ArgusTab.LOGGER)
    val selectedTab: StateFlow<ArgusTab> = _selectedTab.asStateFlow()

    private val _sessionState = MutableStateFlow(LoggerSessionState())
    val sessionState: StateFlow<LoggerSessionState> = _sessionState.asStateFlow()

    private val _sandboxText = MutableStateFlow("")
    val sandboxText: StateFlow<String> = _sandboxText.asStateFlow()

    private val _vaultState = MutableStateFlow(
        VaultUiState(
            todaysMd5FileName = repository.getTodaysMd5FileName(),
            todaysDateLabel = repository.getTodaysDateString()
        )
    )
    val vaultState: StateFlow<VaultUiState> = _vaultState.asStateFlow()

    private val _decryptorState = MutableStateFlow(DecryptorUiState())
    val decryptorState: StateFlow<DecryptorUiState> = _decryptorState.asStateFlow()

    private val _keysState = MutableStateFlow(KeysUiState())
    val keysState: StateFlow<KeysUiState> = _keysState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        initializeWorkspace()
    }

    private fun initializeWorkspace() {
        viewModelScope.launch {
            var storedKeys = repository.loadKeyPairs()
            if (storedKeys.isEmpty()) {
                val defaultGenerated = withContext(Dispatchers.Default) {
                    RsaCryptoEngine.generateKeyPair(keySizeBits = 2048, passphrase = "")
                }
                val defaultRecord = StoredKeyPair(
                    id = UUID.randomUUID().toString(),
                    label = "Argus Workspace Default",
                    createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    keySizeBits = 2048,
                    isPassphraseProtected = false,
                    publicKeyPem = defaultGenerated.publicKeyPem,
                    privateKeyPem = defaultGenerated.privateKeyPem
                )
                storedKeys = listOf(defaultRecord)
                repository.saveKeyPairs(storedKeys)
                repository.updateSettings { it.copy(activeKeyId = defaultRecord.id) }
            }

            val settings = repository.settingsFlow.first()
            val resolvedActiveId = settings.activeKeyId?.takeIf { id -> storedKeys.any { it.id == id } }
                ?: storedKeys.firstOrNull()?.id

            _keysState.update {
                it.copy(
                    keyPairs = storedKeys,
                    activeKeyId = resolvedActiveId,
                    customPublicKeyPem = settings.customPublicKeyPem
                )
            }

            val activePubPem = resolveActivePublicKeyPem(
                keys = storedKeys,
                activeKeyId = resolvedActiveId,
                customPublicKeyPem = settings.customPublicKeyPem
            )

            _sessionState.update { current ->
                val synced = current.copy(
                    mode = settings.mode,
                    encryptionEnabled = settings.encryptionEnabled,
                    initialLanguage = settings.initialLanguage,
                    targetLanguage = settings.targetLanguage,
                    clipboardMonitoringEnabled = settings.clipboardMonitoring
                )
                logEngine.recalculateCharLimit(synced, activePubPem)
            }

            _vaultState.update {
                it.copy(uploadEndpointUrl = settings.uploadEndpointUrl)
            }

            // Pre-populate Decryptor private key with active stored key if none chosen
            storedKeys.firstOrNull { it.id == resolvedActiveId }?.let { activePair ->
                if (_decryptorState.value.privateKeyPemInput.isBlank() && activePair.privateKeyPem.isNotBlank()) {
                    _decryptorState.update {
                        it.copy(
                            privateKeyPemInput = activePair.privateKeyPem,
                            privateKeySourceLabel = "${activePair.label} (${activePair.keySizeBits}-bit)"
                        )
                    }
                }
            }

            refreshVaultLogs()
        }
    }

    fun selectTab(tab: ArgusTab) {
        _selectedTab.value = tab
        if (tab == ArgusTab.VAULT) {
            refreshVaultLogs()
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private fun getActivePublicKeyPem(): String? {
        val ks = _keysState.value
        return resolveActivePublicKeyPem(ks.keyPairs, ks.activeKeyId, ks.customPublicKeyPem)
    }

    private fun resolveActivePublicKeyPem(
        keys: List<StoredKeyPair>,
        activeKeyId: String?,
        customPublicKeyPem: String?
    ): String? {
        if (!customPublicKeyPem.isNullOrBlank()) return customPublicKeyPem
        return keys.firstOrNull { it.id == activeKeyId }?.publicKeyPem
            ?: keys.firstOrNull()?.publicKeyPem
    }

    // --- LOGGER ACTIONS ---

    fun setLoggerMode(mode: LoggerMode) {
        _sessionState.update { it.copy(mode = mode) }
        viewModelScope.launch {
            repository.updateSettings { it.copy(mode = mode) }
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        val pubPem = getActivePublicKeyPem()
        _sessionState.update { current ->
            logEngine.recalculateCharLimit(current.copy(encryptionEnabled = enabled), pubPem)
        }
        viewModelScope.launch {
            repository.updateSettings { it.copy(encryptionEnabled = enabled) }
        }
    }

    fun setActiveWindowName(windowName: String) {
        _sessionState.update { it.copy(activeWindowName = windowName.ifBlank { "Argus Sandbox" }) }
    }

    fun setInitialLanguage(language: String) {
        _sessionState.update { it.copy(initialLanguage = language) }
        viewModelScope.launch {
            repository.updateSettings { it.copy(initialLanguage = language) }
        }
    }

    fun setTargetLanguage(language: String) {
        _sessionState.update { it.copy(targetLanguage = language) }
        viewModelScope.launch {
            repository.updateSettings { it.copy(targetLanguage = language) }
        }
    }

    fun setClipboardMonitoring(enabled: Boolean) {
        _sessionState.update { it.copy(clipboardMonitoringEnabled = enabled) }
        viewModelScope.launch {
            repository.updateSettings { it.copy(clipboardMonitoring = enabled) }
        }
    }

    fun toggleShift() {
        _sessionState.update { it.copy(shiftOn = !it.shiftOn) }
    }

    fun toggleCapsLock() {
        _sessionState.update { it.copy(capsLockOn = !it.capsLockOn) }
    }

    fun toggleCtrl() {
        _sessionState.update { it.copy(ctrlOn = !it.ctrlOn) }
    }

    fun onSandboxTextChanged(newText: String, clipboardText: String?) {
        val oldText = _sandboxText.value
        if (newText == oldText) return
        _sandboxText.value = newText

        var commonPrefix = 0
        val minLen = minOf(oldText.length, newText.length)
        while (commonPrefix < minLen && oldText[commonPrefix] == newText[commonPrefix]) {
            commonPrefix++
        }

        val removedCount = oldText.length - commonPrefix
        val addedSuffix = newText.substring(commonPrefix)

        val pubPem = getActivePublicKeyPem()
        var state = _sessionState.value

        repeat(removedCount) {
            state = logEngine.onSpecialKey(state, SpecialKey.BACKSPACE, clipboardText, pubPem, ::persistLocalPayload)
        }

        for (ch in addedSuffix) {
            state = when (ch) {
                '\n' -> logEngine.onSpecialKey(state, SpecialKey.ENTER, clipboardText, pubPem, ::persistLocalPayload)
                '\t' -> logEngine.onSpecialKey(state, SpecialKey.TAB, clipboardText, pubPem, ::persistLocalPayload)
                ' ' -> logEngine.onSpecialKey(state, SpecialKey.SPACE, clipboardText, pubPem, ::persistLocalPayload)
                else -> logEngine.onCharacterTyped(state, ch, clipboardText, pubPem, ::persistLocalPayload)
            }
        }
        _sessionState.value = state
    }

    fun onSpecialKeyPressed(key: SpecialKey, clipboardText: String?) {
        val pubPem = getActivePublicKeyPem()
        _sessionState.update { state ->
            logEngine.onSpecialKey(state, key, clipboardText, pubPem, ::persistLocalPayload)
        }
        if (key == SpecialKey.ENTER) {
            _sandboxText.value += "\n"
        } else if (key == SpecialKey.SPACE) {
            _sandboxText.value += " "
        } else if (key == SpecialKey.BACKSPACE && _sandboxText.value.isNotEmpty()) {
            _sandboxText.value = _sandboxText.value.dropLast(1)
        }
    }

    fun flushBuffer() {
        val pubPem = getActivePublicKeyPem()
        _sessionState.update { state ->
            logEngine.flushNow(state, pubPem, ::persistLocalPayload)
        }
    }

    fun clearSessionFeed() {
        _sessionState.update { it.copy(flushedFeed = emptyList(), lastError = null) }
        _sandboxText.value = ""
    }

    private fun persistLocalPayload(payload: String) {
        viewModelScope.launch {
            repository.appendToDailyLog(payload)
            refreshVaultLogs()
        }
    }

    // --- VAULT ACTIONS ---

    fun refreshVaultLogs() {
        viewModelScope.launch {
            val files = repository.listVaultLogs()
            _vaultState.update {
                it.copy(
                    logs = files,
                    todaysMd5FileName = repository.getTodaysMd5FileName(),
                    todaysDateLabel = repository.getTodaysDateString()
                )
            }
        }
    }

    fun inspectVaultLog(fileName: String) {
        viewModelScope.launch {
            val content = repository.readLogFile(fileName)
            _vaultState.update {
                it.copy(selectedLogFileName = fileName, selectedLogContent = content)
            }
        }
    }

    fun dismissVaultPreview() {
        _vaultState.update {
            it.copy(selectedLogFileName = null, selectedLogContent = null)
        }
    }

    fun deleteVaultLog(fileName: String) {
        viewModelScope.launch {
            repository.deleteLogFile(fileName)
            if (_vaultState.value.selectedLogFileName == fileName) {
                dismissVaultPreview()
            }
            refreshVaultLogs()
            _snackbarMessage.value = "Deleted $fileName"
        }
    }

    fun sendVaultLogToDecryptor(fileName: String) {
        viewModelScope.launch {
            val content = repository.readLogFile(fileName)
            _decryptorState.update {
                it.copy(
                    encryptedLogInput = content,
                    encryptedSourceLabel = fileName,
                    errorMessage = null
                )
            }
            _selectedTab.value = ArgusTab.DECRYPTOR
        }
    }

    fun setUploadEndpointUrl(url: String) {
        _vaultState.update { it.copy(uploadEndpointUrl = url) }
        viewModelScope.launch {
            repository.updateSettings { it.copy(uploadEndpointUrl = url) }
        }
    }

    fun uploadVaultLog(fileName: String) {
        val url = _vaultState.value.uploadEndpointUrl
        viewModelScope.launch {
            _vaultState.update { it.copy(isUploading = true, networkResult = null) }
            val result = repository.uploadLogToEndpoint(fileName, url)
            _vaultState.update { it.copy(isUploading = false, networkResult = result) }
        }
    }

    // --- DECRYPTOR ACTIONS ---

    fun updateDecryptEncryptedInput(text: String, sourceLabel: String = "Manual / Pasted") {
        _decryptorState.update {
            it.copy(encryptedLogInput = text, encryptedSourceLabel = sourceLabel, errorMessage = null)
        }
    }

    fun updateDecryptPrivateKey(pem: String, sourceLabel: String = "Custom PEM") {
        _decryptorState.update {
            it.copy(privateKeyPemInput = pem, privateKeySourceLabel = sourceLabel, errorMessage = null)
        }
    }

    fun selectStoredKeyForDecryption(pair: StoredKeyPair) {
        if (pair.privateKeyPem.isBlank()) {
            _snackbarMessage.value = "Selected entry has a public key only."
            return
        }
        _decryptorState.update {
            it.copy(
                privateKeyPemInput = pair.privateKeyPem,
                privateKeySourceLabel = "${pair.label} (${pair.keySizeBits}-bit)",
                errorMessage = null
            )
        }
    }

    fun updateDecryptPassphrase(passphrase: String) {
        _decryptorState.update { it.copy(passphrase = passphrase, errorMessage = null) }
    }

    fun decryptLog() {
        val state = _decryptorState.value
        if (state.encryptedLogInput.isBlank()) {
            _decryptorState.update { it.copy(errorMessage = "Provide an encrypted log file or ---START---...---END--- payload.") }
            return
        }
        if (state.privateKeyPemInput.isBlank()) {
            _decryptorState.update { it.copy(errorMessage = "Select or paste an RSA private key PEM.") }
            return
        }

        viewModelScope.launch {
            _decryptorState.update { it.copy(isDecrypting = true, errorMessage = null) }
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    RsaCryptoEngine.decryptMany(
                        encryptedLog = state.encryptedLogInput,
                        privateKeyPem = state.privateKeyPemInput,
                        passphrase = state.passphrase
                    )
                }
            }
            outcome.onSuccess { res ->
                _decryptorState.update {
                    it.copy(
                        isDecrypting = false,
                        result = res,
                        errorMessage = if (res.totalChunks == 0) "No ---START---...---END--- encrypted chunks found in log." else null
                    )
                }
            }.onFailure { err ->
                _decryptorState.update {
                    it.copy(
                        isDecrypting = false,
                        errorMessage = err.message ?: "Failed to load private key or decrypt."
                    )
                }
            }
        }
    }

    fun clearDecryptionOutput() {
        _decryptorState.update {
            it.copy(result = null, passphrase = "", errorMessage = null)
        }
        _snackbarMessage.value = "Decryption output cleared"
    }

    // --- KEYS ACTIONS ---

    fun generateNewKeyPair(
        label: String,
        keySizeBits: Int,
        passphrase: String,
        confirmPassphrase: String,
        setAsActive: Boolean
    ) {
        if (passphrase != confirmPassphrase) {
            _keysState.update { it.copy(errorMessage = "Passphrases do not match!") }
            return
        }
        val cleanLabel = label.trim().ifEmpty { "RSA-$keySizeBits Key" }
        viewModelScope.launch {
            _keysState.update { it.copy(isGenerating = true, errorMessage = null, statusMessage = null) }
            val outcome = withContext(Dispatchers.Default) {
                runCatching { RsaCryptoEngine.generateKeyPair(keySizeBits, passphrase) }
            }
            outcome.onSuccess { generated ->
                val record = StoredKeyPair(
                    id = UUID.randomUUID().toString(),
                    label = cleanLabel,
                    createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    keySizeBits = generated.keySizeBits,
                    isPassphraseProtected = generated.isPassphraseProtected,
                    publicKeyPem = generated.publicKeyPem,
                    privateKeyPem = generated.privateKeyPem
                )
                val updatedList = listOf(record) + _keysState.value.keyPairs
                repository.saveKeyPairs(updatedList)
                val nextActiveId = if (setAsActive) record.id else _keysState.value.activeKeyId
                val nextCustomPub = if (setAsActive) null else _keysState.value.customPublicKeyPem
                if (setAsActive) {
                    repository.updateSettings {
                        it.copy(activeKeyId = record.id, customPublicKeyPem = null)
                    }
                    _sessionState.update { s ->
                        logEngine.recalculateCharLimit(s, record.publicKeyPem)
                    }
                }
                _keysState.update {
                    it.copy(
                        keyPairs = updatedList,
                        activeKeyId = nextActiveId,
                        customPublicKeyPem = nextCustomPub,
                        isGenerating = false,
                        statusMessage = "Generated ${generated.keySizeBits}-bit RSA keypair"
                    )
                }
            }.onFailure { err ->
                _keysState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = err.message ?: "Key generation failed"
                    )
                }
            }
        }
    }

    fun setActiveKeyPair(pair: StoredKeyPair) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(activeKeyId = pair.id, customPublicKeyPem = null)
            }
            _keysState.update {
                it.copy(
                    activeKeyId = pair.id,
                    customPublicKeyPem = null,
                    statusMessage = "Active encryption key: ${pair.label}",
                    errorMessage = null
                )
            }
            _sessionState.update { s ->
                logEngine.recalculateCharLimit(s, pair.publicKeyPem)
            }
        }
    }

    fun importPublicKeyPem(pem: String, label: String = "Imported Public Key") {
        val bits = RsaCryptoEngine.getPublicKeyBitLength(pem)
        if (bits == null) {
            _keysState.update { it.copy(errorMessage = "Invalid RSA Public Key PEM.") }
            return
        }
        viewModelScope.launch {
            val record = StoredKeyPair(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { "Imported Public Key" },
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                keySizeBits = bits,
                isPassphraseProtected = false,
                publicKeyPem = pem.trim(),
                privateKeyPem = ""
            )
            val updated = listOf(record) + _keysState.value.keyPairs
            repository.saveKeyPairs(updated)
            repository.updateSettings {
                it.copy(activeKeyId = record.id, customPublicKeyPem = null)
            }
            _keysState.update {
                it.copy(
                    keyPairs = updated,
                    activeKeyId = record.id,
                    customPublicKeyPem = null,
                    statusMessage = "Imported $bits-bit public key & activated",
                    errorMessage = null
                )
            }
            _sessionState.update { s ->
                logEngine.recalculateCharLimit(s, record.publicKeyPem)
            }
        }
    }

    fun deleteKeyPair(id: String) {
        viewModelScope.launch {
            val updated = _keysState.value.keyPairs.filterNot { it.id == id }
            repository.saveKeyPairs(updated)
            val newActive = if (_keysState.value.activeKeyId == id) updated.firstOrNull()?.id else _keysState.value.activeKeyId
            repository.updateSettings { it.copy(activeKeyId = newActive) }
            _keysState.update {
                it.copy(keyPairs = updated, activeKeyId = newActive, statusMessage = "Removed keypair")
            }
            val pubPem = resolveActivePublicKeyPem(updated, newActive, _keysState.value.customPublicKeyPem)
            _sessionState.update { s -> logEngine.recalculateCharLimit(s, pubPem) }
        }
    }

    fun clearKeyMessages() {
        _keysState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
