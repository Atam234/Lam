package com.example.argus.ai

import android.util.Log
import com.example.argus.model.AiProviderProfile
import com.example.argus.model.ChatMessage
import com.example.argus.model.MessageRole
import com.example.argus.model.ProviderProtocol
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "MultiModelAiEngine"

class MultiModelAiEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun streamCompletion(
        provider: AiProviderProfile,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<String> {
        return when (provider.protocol) {
            ProviderProtocol.FIREBASE_GEMINI -> streamFirebaseGemini(
                provider = provider,
                systemPrompt = systemPrompt,
                history = history,
                userPrompt = userPrompt
            )
            ProviderProtocol.OPENAI_COMPATIBLE -> streamOpenAiCompatible(
                provider = provider,
                systemPrompt = systemPrompt,
                history = history,
                userPrompt = userPrompt
            )
            ProviderProtocol.ANTHROPIC -> streamAnthropic(
                provider = provider,
                systemPrompt = systemPrompt,
                history = history,
                userPrompt = userPrompt
            )
            ProviderProtocol.GEMINI_REST -> streamGeminiRest(
                provider = provider,
                systemPrompt = systemPrompt,
                history = history,
                userPrompt = userPrompt
            )
        }
    }

    suspend fun testConnection(provider: AiProviderProfile): Result<String> {
        return try {
            val sb = StringBuilder()
            withTimeout(30_000L) {
                streamCompletion(
                    provider = provider,
                    systemPrompt = "You are a connectivity test assistant. Reply briefly.",
                    history = emptyList(),
                    userPrompt = "Reply with OK and your model name."
                ).collect { chunk ->
                    sb.append(chunk)
                }
            }
            val output = sb.toString().trim()
            if (output.isEmpty()) {
                Result.failure(IllegalStateException("Connected, but model returned an empty response."))
            } else {
                Result.success(output)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Connection test timed out", e)
            Result.failure(IllegalStateException("Connection test timed out after 30 seconds."))
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(e)
        }
    }

    private fun streamFirebaseGemini(
        provider: AiProviderProfile,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<String> = flow {
        val model by lazy {
            Firebase.ai.generativeModel(
                modelName = provider.modelId.ifBlank { "gemini-3.8-flash" },
                generationConfig = generationConfig {
                    temperature = provider.temperature
                    maxOutputTokens = provider.maxTokens
                },
                systemInstruction = if (systemPrompt.isNotBlank()) {
                    content { text(systemPrompt) }
                } else {
                    null
                }
            )
        }

        val chatHistory = history
            .filter { it.role == MessageRole.USER || it.role == MessageRole.MODEL }
            .map { msg ->
                content(role = if (msg.role == MessageRole.USER) "user" else "model") {
                    text(msg.text)
                }
            }

        withTimeout(60_000L) {
            val chat = model.startChat(history = chatHistory)
            chat.sendMessageStream(userPrompt).collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOpenAiCompatible(
        provider: AiProviderProfile,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<String> = flow {
        val baseUrl = provider.baseUrl.trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Invalid Base URL. Must start with http:// or https://"
        }
        val endpoint = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

        val messagesJson = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messagesJson.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        history
            .filter { it.role == MessageRole.USER || it.role == MessageRole.MODEL }
            .forEach { msg ->
                messagesJson.put(
                    JSONObject()
                        .put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                        .put("content", msg.text)
                )
            }
        messagesJson.put(JSONObject().put("role", "user").put("content", userPrompt))

        val payload = JSONObject()
            .put("model", provider.modelId.trim())
            .put("messages", messagesJson)
            .put("temperature", provider.temperature.toDouble())
            .put("max_tokens", provider.maxTokens)
            .put("stream", true)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        if (provider.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey.trim()}")
        }
        if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
            requestBuilder.header("X-Title", "OmniMode Android")
        }

        withTimeout(60_000L) {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException("HTTP ${response.code}: ${extractReadableApiError(errBody)}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    val raw = body.string()
                    val parsed = extractOpenAiNonStreamingContent(raw)
                    if (parsed.isNotEmpty()) emit(parsed)
                    return@use
                }

                BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim().orEmpty()
                        if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val deltaText = extractOpenAiDelta(data)
                        if (deltaText.isNotEmpty()) {
                            emit(deltaText)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamAnthropic(
        provider: AiProviderProfile,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<String> = flow {
        val baseUrl = provider.baseUrl.trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Invalid Base URL. Must start with http:// or https://"
        }
        require(provider.apiKey.isNotBlank()) {
            "Anthropic API key is required."
        }
        val endpoint = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/v1/messages"

        val messagesJson = JSONArray()
        history
            .filter { it.role == MessageRole.USER || it.role == MessageRole.MODEL }
            .forEach { msg ->
                messagesJson.put(
                    JSONObject()
                        .put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                        .put("content", msg.text)
                )
            }
        messagesJson.put(JSONObject().put("role", "user").put("content", userPrompt))

        val payload = JSONObject()
            .put("model", provider.modelId.trim())
            .put("max_tokens", provider.maxTokens)
            .put("temperature", provider.temperature.toDouble())
            .put("stream", true)
            .put("messages", messagesJson)
        if (systemPrompt.isNotBlank()) {
            payload.put("system", systemPrompt)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("x-api-key", provider.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .build()

        withTimeout(60_000L) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException("HTTP ${response.code}: ${extractReadableApiError(errBody)}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        val text = extractAnthropicDelta(data)
                        if (text.isNotEmpty()) emit(text)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamGeminiRest(
        provider: AiProviderProfile,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): Flow<String> = flow {
        val baseUrl = provider.baseUrl.trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Invalid Base URL. Must start with http:// or https://"
        }
        require(provider.apiKey.isNotBlank()) {
            "API key is required for Gemini REST provider."
        }
        val modelId = provider.modelId.trim().removePrefix("models/")
        val endpoint = "$baseUrl/v1beta/models/$modelId:streamGenerateContent?alt=sse&key=${provider.apiKey.trim()}"

        val contentsArray = JSONArray()
        history
            .filter { it.role == MessageRole.USER || it.role == MessageRole.MODEL }
            .forEach { msg ->
                contentsArray.put(
                    JSONObject()
                        .put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                )
            }
        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
        )

        val payload = JSONObject()
            .put("contents", contentsArray)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", provider.temperature.toDouble())
                    .put("maxOutputTokens", provider.maxTokens)
            )
        if (systemPrompt.isNotBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        withTimeout(60_000L) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException("HTTP ${response.code}: ${extractReadableApiError(errBody)}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        val text = extractGeminiRestText(data)
                        if (text.isNotEmpty()) emit(text)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun extractOpenAiDelta(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val choices = root.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val choice = choices.getJSONObject(0)
            val delta = choice.optJSONObject("delta")
            if (delta != null && delta.has("content") && !delta.isNull("content")) {
                return delta.optString("content", "")
            }
            val message = choice.optJSONObject("message")
            message?.optString("content", "").orEmpty()
        }.getOrDefault("")
    }

    private fun extractOpenAiNonStreamingContent(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val choices = root.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "").orEmpty()
        }.getOrDefault("")
    }

    private fun extractAnthropicDelta(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val type = root.optString("type")
            if (type == "content_block_delta") {
                root.optJSONObject("delta")?.optString("text", "").orEmpty()
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun extractGeminiRestText(jsonStr: String): String {
        return runCatching {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString()
        }.getOrDefault("")
    }

    private fun extractReadableApiError(rawBody: String): String {
        if (rawBody.isBlank()) return "Request rejected by API server"
        return runCatching {
            val json = JSONObject(rawBody)
            when {
                json.has("error") -> {
                    val err = json.get("error")
                    if (err is JSONObject) err.optString("message", rawBody) else err.toString()
                }
                json.has("message") -> json.optString("message", rawBody)
                else -> rawBody.take(240)
            }
        }.getOrDefault(rawBody.take(240))
    }
}
