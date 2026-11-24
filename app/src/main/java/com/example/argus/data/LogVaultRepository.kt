package com.example.argus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.argus.logger.LoggerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "argus_settings")

@Serializable
data class StoredKeyPair(
    val id: String,
    val label: String,
    val createdAt: String,
    val keySizeBits: Int,
    val isPassphraseProtected: Boolean,
    val publicKeyPem: String,
    val privateKeyPem: String
)

data class VaultLogFile(
    val fileName: String,
    val dateLabel: String?,
    val sizeBytes: Long,
    val chunkCount: Int,
    val lastModifiedMillis: Long
)

data class ArgusSettings(
    val mode: LoggerMode = LoggerMode.LOCAL,
    val encryptionEnabled: Boolean = true,
    val initialLanguage: String = "English - United States",
    val targetLanguage: String = "English - United States",
    val clipboardMonitoring: Boolean = false,
    val activeKeyId: String? = null,
    val customPublicKeyPem: String? = null,
    val uploadEndpointUrl: String = ""
)

data class NetworkDiagnosticResult(
    val internetReachable: Boolean,
    val publicIp: String?,
    val uploadStatusCode: Int?,
    val message: String
)

class LogVaultRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val logsDir: File
        get() = File(context.filesDir, "logs").apply { mkdirs() }

    private val keysFile: File
        get() = File(context.filesDir, "keys/keypairs.json").apply { parentFile?.mkdirs() }

    private val logIndexFile: File
        get() = File(context.filesDir, "logs/index.json").apply { parentFile?.mkdirs() }

    private object PrefKeys {
        val MODE = stringPreferencesKey("logger_mode")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val INITIAL_LANGUAGE = stringPreferencesKey("initial_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val CLIPBOARD_MONITORING = booleanPreferencesKey("clipboard_monitoring")
        val ACTIVE_KEY_ID = stringPreferencesKey("active_key_id")
        val CUSTOM_PUBLIC_KEY = stringPreferencesKey("custom_public_key_pem")
        val UPLOAD_ENDPOINT = stringPreferencesKey("upload_endpoint_url")
    }

    val settingsFlow: Flow<ArgusSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[PrefKeys.MODE] ?: LoggerMode.LOCAL.name
        ArgusSettings(
            mode = runCatching { LoggerMode.valueOf(modeName) }.getOrDefault(LoggerMode.LOCAL),
            encryptionEnabled = prefs[PrefKeys.ENCRYPTION_ENABLED] ?: true,
            initialLanguage = prefs[PrefKeys.INITIAL_LANGUAGE] ?: "English - United States",
            targetLanguage = prefs[PrefKeys.TARGET_LANGUAGE] ?: "English - United States",
            clipboardMonitoring = prefs[PrefKeys.CLIPBOARD_MONITORING] ?: false,
            activeKeyId = prefs[PrefKeys.ACTIVE_KEY_ID],
            customPublicKeyPem = prefs[PrefKeys.CUSTOM_PUBLIC_KEY],
            uploadEndpointUrl = prefs[PrefKeys.UPLOAD_ENDPOINT] ?: ""
        )
    }

    suspend fun updateSettings(transform: (ArgusSettings) -> ArgusSettings) {
        context.dataStore.edit { prefs ->
            val current = ArgusSettings(
                mode = runCatching {
                    LoggerMode.valueOf(prefs[PrefKeys.MODE] ?: LoggerMode.LOCAL.name)
                }.getOrDefault(LoggerMode.LOCAL),
                encryptionEnabled = prefs[PrefKeys.ENCRYPTION_ENABLED] ?: true,
                initialLanguage = prefs[PrefKeys.INITIAL_LANGUAGE] ?: "English - United States",
                targetLanguage = prefs[PrefKeys.TARGET_LANGUAGE] ?: "English - United States",
                clipboardMonitoring = prefs[PrefKeys.CLIPBOARD_MONITORING] ?: false,
                activeKeyId = prefs[PrefKeys.ACTIVE_KEY_ID],
                customPublicKeyPem = prefs[PrefKeys.CUSTOM_PUBLIC_KEY],
                uploadEndpointUrl = prefs[PrefKeys.UPLOAD_ENDPOINT] ?: ""
            )
            val updated = transform(current)
            prefs[PrefKeys.MODE] = updated.mode.name
            prefs[PrefKeys.ENCRYPTION_ENABLED] = updated.encryptionEnabled
            prefs[PrefKeys.INITIAL_LANGUAGE] = updated.initialLanguage
            prefs[PrefKeys.TARGET_LANGUAGE] = updated.targetLanguage
            prefs[PrefKeys.CLIPBOARD_MONITORING] = updated.clipboardMonitoring
            if (updated.activeKeyId != null) {
                prefs[PrefKeys.ACTIVE_KEY_ID] = updated.activeKeyId
            } else {
                prefs.remove(PrefKeys.ACTIVE_KEY_ID)
            }
            if (updated.customPublicKeyPem != null) {
                prefs[PrefKeys.CUSTOM_PUBLIC_KEY] = updated.customPublicKeyPem
            } else {
                prefs.remove(PrefKeys.CUSTOM_PUBLIC_KEY)
            }
            prefs[PrefKeys.UPLOAD_ENDPOINT] = updated.uploadEndpointUrl
        }
    }

    /**
     * Mirrors start.py / logger_light.py:
     * Filename is MD5(today.strftime('%Y-%b-%d')) + '.txt'
     */
    fun getTodaysDateString(): String {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.US))
    }

    fun getTodaysMd5FileName(): String {
        return md5Hex(getTodaysDateString()) + ".txt"
    }

    suspend fun appendToDailyLog(payload: String) = withContext(Dispatchers.IO) {
        val dateLabel = getTodaysDateString()
        val fileName = md5Hex(dateLabel) + ".txt"
        val target = File(logsDir, fileName)
        target.appendText(payload, Charsets.UTF_8)
        recordDateIndex(fileName, dateLabel)
    }

    suspend fun listVaultLogs(): List<VaultLogFile> = withContext(Dispatchers.IO) {
        val index = loadDateIndex()
        val files = logsDir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?: emptyArray()
        files.sortedByDescending { it.lastModified() }.map { file ->
            val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            val chunks = "---START---".toRegex().findAll(content).count()
            VaultLogFile(
                fileName = file.name,
                dateLabel = index[file.name] ?: resolveKnownDateFromMd5(file.nameWithoutExtension),
                sizeBytes = file.length(),
                chunkCount = chunks,
                lastModifiedMillis = file.lastModified()
            )
        }
    }

    suspend fun readLogFile(fileName: String): String = withContext(Dispatchers.IO) {
        val file = File(logsDir, fileName)
        if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    suspend fun deleteLogFile(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(logsDir, fileName)
        if (file.exists()) file.delete()
    }

    suspend fun loadKeyPairs(): List<StoredKeyPair> = withContext(Dispatchers.IO) {
        if (!keysFile.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<StoredKeyPair>>(keysFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    suspend fun saveKeyPairs(items: List<StoredKeyPair>) = withContext(Dispatchers.IO) {
        keysFile.writeText(json.encodeToString(items), Charsets.UTF_8)
    }

    /**
     * Explicit user-triggered upload / connectivity check mirroring start.py get_my_ip() & upload POST.
     */
    suspend fun uploadLogToEndpoint(
        fileName: String,
        endpointUrl: String
    ): NetworkDiagnosticResult = withContext(Dispatchers.IO) {
        val trimmedUrl = endpointUrl.trim()
        if (trimmedUrl.isEmpty() || (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://"))) {
            return@withContext NetworkDiagnosticResult(
                internetReachable = false,
                publicIp = null,
                uploadStatusCode = null,
                message = "Enter a valid http:// or https:// endpoint URL."
            )
        }
        val file = File(logsDir, fileName)
        if (!file.exists()) {
            return@withContext NetworkDiagnosticResult(
                internetReachable = false,
                publicIp = null,
                uploadStatusCode = null,
                message = "Log file not found."
            )
        }
        val publicIp = fetchPublicIp()
        try {
            val conn = (URL(trimmedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setRequestProperty("X-Argus-Log-File", fileName)
                if (!publicIp.isNullOrBlank()) {
                    setRequestProperty("X-Argus-Client-IP", publicIp)
                }
            }
            conn.outputStream.use { os ->
                os.write(file.readBytes())
            }
            val code = conn.responseCode
            conn.disconnect()
            NetworkDiagnosticResult(
                internetReachable = true,
                publicIp = publicIp,
                uploadStatusCode = code,
                message = if (code in 200..299) {
                    "Uploaded $fileName (HTTP $code)"
                } else {
                    "Server responded with HTTP $code"
                }
            )
        } catch (e: Exception) {
            NetworkDiagnosticResult(
                internetReachable = publicIp != null,
                publicIp = publicIp,
                uploadStatusCode = null,
                message = "Upload failed: ${e.message ?: "Connection error"}"
            )
        }
    }

    private fun fetchPublicIp(): String? {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://ip.42.pl/raw"
        )
        for (url in endpoints) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText().trim()
                    conn.disconnect()
                    if (text.isNotEmpty() && text.length <= 45) return text
                }
                conn.disconnect()
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun loadDateIndex(): MutableMap<String, String> {
        if (!logIndexFile.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, String>>(logIndexFile.readText(Charsets.UTF_8)).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun recordDateIndex(fileName: String, dateLabel: String) {
        val map = loadDateIndex()
        map[fileName] = dateLabel
        logIndexFile.writeText(json.encodeToString(map.toMap()), Charsets.UTF_8)
    }

    private fun resolveKnownDateFromMd5(md5Target: String): String? {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.US)
        val today = LocalDate.now()
        for (delta in 0..366L) {
            val candidate = today.minusDays(delta).format(formatter)
            if (md5Hex(candidate) == md5Target) return candidate
        }
        return null
    }

    companion object {
        fun md5Hex(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
