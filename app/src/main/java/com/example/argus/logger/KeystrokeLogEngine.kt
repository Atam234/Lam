package com.example.argus.logger

import com.example.argus.crypto.RsaCryptoEngine
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LoggerMode(val cliFlag: String, val label: String) {
    LOCAL("local", "Local Vault"),
    DEBUG("debug", "Debug Console")
}

enum class SpecialKey(val tag: String) {
    ENTER("<ENTER>"),
    TAB("<TAB>"),
    BACKSPACE("<BACKSPACE>"),
    SPACE(" "),
    LEFT("left"),
    RIGHT("right")
}

data class FlushedLogEntry(
    val id: Long,
    val timestamp: String,
    val mode: LoggerMode,
    val isEncrypted: Boolean,
    val plaintextPreview: String,
    val payload: String,
    val byteCount: Int
)

data class LoggerSessionState(
    val mode: LoggerMode = LoggerMode.LOCAL,
    val encryptionEnabled: Boolean = true,
    val activeWindowName: String = "Argus Sandbox",
    val initialLanguage: String = "English - United States",
    val targetLanguage: String = "English - United States",
    val clipboardMonitoringEnabled: Boolean = false,
    val shiftOn: Boolean = false,
    val capsLockOn: Boolean = false,
    val ctrlOn: Boolean = false,
    val lineBuffer: String = "",
    val backspaceBufferLen: Int = 0,
    val charLimit: Int = 126,
    val minutesToLogTime: Int = 5,
    val lastTimeLogged: LocalDateTime = LocalDateTime.now().minusMinutes(5),
    val lastLoggedWindow: String = "",
    val lastLoggedClipboard: String = "",
    val flushedFeed: List<FlushedLogEntry> = emptyList(),
    val lastError: String? = null
) {
    val upperCase: Boolean
        get() = KeyboardLayoutTranslator.updateUpperCase(capsLockOn, shiftOn)

    val currentBytes: Int
        get() = lineBuffer.toByteArray(Charsets.UTF_8).size
}

/**
 * Faithful state-machine port of start.py / logger_light.py key_callback, backspace_buffer_len,
 * [WindowName], [Time], [Clipboard] tags, and CHAR_LIMIT flush triggers.
 */
class KeystrokeLogEngine {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val feedTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var entryCounter = 0L

    fun recalculateCharLimit(state: LoggerSessionState, publicKeyPem: String?): LoggerSessionState {
        val limit = if (state.encryptionEnabled && !publicKeyPem.isNullOrBlank()) {
            runCatching { RsaCryptoEngine.computeCharLimit(publicKeyPem) }.getOrDefault(126)
        } else {
            20 // logger_light.py CHAR_LIMIT = 20
        }
        return state.copy(charLimit = limit)
    }

    fun onCharacterTyped(
        state: LoggerSessionState,
        rawChar: Char,
        clipboardText: String?,
        publicKeyPem: String?,
        onPersistLocal: (String) -> Unit
    ): LoggerSessionState {
        var next = prepareMetadataPrefixes(state, clipboardText, publicKeyPem, onPersistLocal)
        val translated = KeyboardLayoutTranslator.translateCharacter(
            ch = rawChar,
            initialLanguage = next.initialLanguage,
            currentLanguage = next.targetLanguage,
            upperCase = next.upperCase
        )
        next = next.copy(lineBuffer = next.lineBuffer + translated)
        return flushIfLimitReached(next, publicKeyPem, onPersistLocal)
    }

    fun onSpecialKey(
        state: LoggerSessionState,
        key: SpecialKey,
        clipboardText: String?,
        publicKeyPem: String?,
        onPersistLocal: (String) -> Unit
    ): LoggerSessionState {
        var next = prepareMetadataPrefixes(state, clipboardText, publicKeyPem, onPersistLocal)
        when (key) {
            SpecialKey.LEFT, SpecialKey.RIGHT -> {
                val mods = buildList {
                    if (next.ctrlOn) add("ctrl")
                    if (next.shiftOn) add("shift")
                }
                val tag = "<" + mods.joinToString("+") + (if (mods.isNotEmpty()) "+" else "") + key.tag + ">"
                val updated = next.lineBuffer + tag
                next = next.copy(
                    lineBuffer = updated,
                    backspaceBufferLen = updated.length
                )
            }
            SpecialKey.SPACE -> {
                next = next.copy(lineBuffer = next.lineBuffer + " ")
            }
            SpecialKey.ENTER, SpecialKey.TAB -> {
                val updated = next.lineBuffer + key.tag
                next = next.copy(
                    lineBuffer = updated,
                    backspaceBufferLen = updated.length
                )
                return flushNow(next, publicKeyPem, onPersistLocal)
            }
            SpecialKey.BACKSPACE -> {
                if (next.lineBuffer.length - next.backspaceBufferLen > 0) {
                    next = next.copy(lineBuffer = next.lineBuffer.dropLast(1))
                } else {
                    val updated = next.lineBuffer + "<BACKSPACE>"
                    next = next.copy(
                        lineBuffer = updated,
                        backspaceBufferLen = updated.length
                    )
                }
            }
        }
        return flushIfLimitReached(next, publicKeyPem, onPersistLocal)
    }

    fun flushNow(
        state: LoggerSessionState,
        publicKeyPem: String?,
        onPersistLocal: (String) -> Unit
    ): LoggerSessionState {
        if (state.lineBuffer.isEmpty()) return state
        val rawText = state.lineBuffer
        val payload: String
        val errorMsg: String?

        if (state.encryptionEnabled) {
            if (publicKeyPem.isNullOrBlank()) {
                return state.copy(lastError = "No active RSA public key configured for encryption")
            }
            val encryptedAttempt = runCatching { RsaCryptoEngine.encrypt(rawText, publicKeyPem) }
            if (encryptedAttempt.isFailure) {
                return state.copy(lastError = encryptedAttempt.exceptionOrNull()?.message ?: "Encryption error")
            }
            payload = encryptedAttempt.getOrThrow()
            errorMsg = null
        } else {
            payload = rawText
            errorMsg = null
        }

        if (state.mode == LoggerMode.LOCAL) {
            onPersistLocal(payload)
        }

        val entry = FlushedLogEntry(
            id = ++entryCounter,
            timestamp = LocalDateTime.now().format(feedTimeFormatter),
            mode = state.mode,
            isEncrypted = state.encryptionEnabled,
            plaintextPreview = rawText,
            payload = payload,
            byteCount = rawText.toByteArray(Charsets.UTF_8).size
        )

        return state.copy(
            lineBuffer = "",
            backspaceBufferLen = 0,
            flushedFeed = (listOf(entry) + state.flushedFeed).take(40),
            lastError = errorMsg
        )
    }

    private fun prepareMetadataPrefixes(
        state: LoggerSessionState,
        clipboardText: String?,
        publicKeyPem: String?,
        onPersistLocal: (String) -> Unit
    ): LoggerSessionState {
        var current = state
        var windowBuffer = ""
        var timeBuffer = ""
        var clipboardBuffer = ""

        if (current.lastLoggedWindow != current.activeWindowName) {
            windowBuffer = "\n[WindowName: ${current.activeWindowName}]: "
            current = current.copy(lastLoggedWindow = current.activeWindowName)
        }

        val now = LocalDateTime.now()
        if (Duration.between(current.lastTimeLogged, now).toMinutes() >= current.minutesToLogTime) {
            timeBuffer = "\n[Time: ${now.format(timeFormatter)}]: "
            current = current.copy(lastTimeLogged = now)
        }

        if (current.clipboardMonitoringEnabled && !clipboardText.isNullOrEmpty() && clipboardText.length < 300) {
            if (clipboardText != current.lastLoggedClipboard) {
                clipboardBuffer = "\n[Clipboard: $clipboardText]: "
                current = current.copy(lastLoggedClipboard = clipboardText)
            }
        }

        if (timeBuffer.isNotEmpty() || windowBuffer.isNotEmpty() || clipboardBuffer.isNotEmpty()) {
            if (current.lineBuffer.isNotEmpty()) {
                current = flushNow(current, publicKeyPem, onPersistLocal)
            }
            val prefix = timeBuffer + windowBuffer + clipboardBuffer
            current = current.copy(
                lineBuffer = prefix,
                backspaceBufferLen = prefix.length
            )
        }
        return current
    }

    private fun flushIfLimitReached(
        state: LoggerSessionState,
        publicKeyPem: String?,
        onPersistLocal: (String) -> Unit
    ): LoggerSessionState {
        return if (state.currentBytes >= state.charLimit) {
            flushNow(state, publicKeyPem, onPersistLocal)
        } else {
            state
        }
    }
}
