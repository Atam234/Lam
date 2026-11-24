package com.example.argus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.argus.model.AiModePreset
import com.example.argus.model.AiProviderProfile
import com.example.argus.model.ProviderProtocol
import com.example.argus.model.RunSessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.omniDataStore: DataStore<Preferences> by preferencesDataStore(name = "omnimode_prefs")

class OmniModeRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val providersFile: File
        get() = File(context.filesDir, "omnimode/providers.json").apply { parentFile?.mkdirs() }

    private val modesFile: File
        get() = File(context.filesDir, "omnimode/modes.json").apply { parentFile?.mkdirs() }

    private val historyFile: File
        get() = File(context.filesDir, "omnimode/history.json").apply { parentFile?.mkdirs() }

    private object PrefKeys {
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val ACTIVE_MODE_ID = stringPreferencesKey("active_mode_id")
    }

    suspend fun getActiveProviderId(): String? =
        context.omniDataStore.data.first()[PrefKeys.ACTIVE_PROVIDER_ID]

    suspend fun setActiveProviderId(id: String) {
        context.omniDataStore.edit { it[PrefKeys.ACTIVE_PROVIDER_ID] = id }
    }

    suspend fun getActiveModeId(): String? =
        context.omniDataStore.data.first()[PrefKeys.ACTIVE_MODE_ID]

    suspend fun setActiveModeId(id: String) {
        context.omniDataStore.edit { it[PrefKeys.ACTIVE_MODE_ID] = id }
    }

    suspend fun loadProviders(): List<AiProviderProfile> = withContext(Dispatchers.IO) {
        if (!providersFile.exists()) {
            val defaults = defaultProviders()
            saveProviders(defaults)
            return@withContext defaults
        }
        runCatching {
            json.decodeFromString<List<AiProviderProfile>>(providersFile.readText(Charsets.UTF_8))
        }.getOrElse { defaultProviders() }
    }

    suspend fun saveProviders(providers: List<AiProviderProfile>) = withContext(Dispatchers.IO) {
        providersFile.writeText(json.encodeToString(providers), Charsets.UTF_8)
    }

    suspend fun loadModes(): List<AiModePreset> = withContext(Dispatchers.IO) {
        if (!modesFile.exists()) {
            val defaults = defaultModes()
            saveModes(defaults)
            return@withContext defaults
        }
        runCatching {
            json.decodeFromString<List<AiModePreset>>(modesFile.readText(Charsets.UTF_8))
        }.getOrElse { defaultModes() }
    }

    suspend fun saveModes(modes: List<AiModePreset>) = withContext(Dispatchers.IO) {
        modesFile.writeText(json.encodeToString(modes), Charsets.UTF_8)
    }

    suspend fun loadHistory(): List<RunSessionRecord> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<RunSessionRecord>>(historyFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    suspend fun saveHistory(records: List<RunSessionRecord>) = withContext(Dispatchers.IO) {
        historyFile.writeText(json.encodeToString(records.take(50)), Charsets.UTF_8)
    }

    companion object {
        fun defaultProviders(): List<AiProviderProfile> = listOf(
            AiProviderProfile(
                id = "firebase_gemini",
                name = "Built-in Gemini",
                protocol = ProviderProtocol.FIREBASE_GEMINI,
                baseUrl = "firebase-ai-logic",
                apiKey = "",
                modelId = "gemini-3.8-flash",
                presetModels = listOf(
                    "gemini-3.8-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-4-argon"
                )
            ),
            AiProviderProfile(
                id = "openrouter",
                name = "OpenRouter (Any Model)",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = "",
                modelId = "openai/gpt-4o-mini",
                presetModels = listOf(
                    "openai/gpt-4o-mini",
                    "anthropic/claude-3.7-sonnet",
                    "deepseek/deepseek-chat",
                    "meta-llama/llama-3.3-70b-instruct",
                    "qwen/qwen-2.5-72b-instruct"
                )
            ),
            AiProviderProfile(
                id = "openai",
                name = "OpenAI",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                modelId = "gpt-4o-mini",
                presetModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1", "o3-mini")
            ),
            AiProviderProfile(
                id = "anthropic",
                name = "Anthropic Claude",
                protocol = ProviderProtocol.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                apiKey = "",
                modelId = "claude-3-7-sonnet-latest",
                presetModels = listOf(
                    "claude-3-7-sonnet-latest",
                    "claude-3-5-haiku-latest",
                    "claude-sonnet-4-0"
                )
            ),
            AiProviderProfile(
                id = "groq",
                name = "Groq Fast Inference",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = "",
                modelId = "llama-3.3-70b-versatile",
                presetModels = listOf(
                    "llama-3.3-70b-versatile",
                    "llama-3.1-8b-instant",
                    "qwen-qwq-32b"
                )
            ),
            AiProviderProfile(
                id = "deepseek",
                name = "DeepSeek",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                modelId = "deepseek-chat",
                presetModels = listOf("deepseek-chat", "deepseek-reasoner")
            ),
            AiProviderProfile(
                id = "ollama_local",
                name = "Ollama / Local Server",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "http://10.0.2.2:11434/v1",
                apiKey = "",
                modelId = "llama3.2",
                presetModels = listOf("llama3.2", "qwen2.5:7b", "mistral", "gemma3")
            )
        )

        fun defaultModes(): List<AiModePreset> = listOf(
            AiModePreset(
                id = "mode_assistant",
                name = "Smart Assistant",
                subtitle = "Natural Q&A in Tagalog, English, or any language",
                systemPrompt = "You are a helpful, accurate, and friendly AI assistant. Respond naturally in the user's language (including Tagalog/Taglish or English) with clear structure.",
                starterPrompts = listOf(
                    "Ipaliwanag ang quantum computing sa simpleng Tagalog",
                    "Gumawa ng 3-day Manila itinerary",
                    "Help me plan a productive morning routine"
                ),
                iconKey = "spark"
            ),
            AiModePreset(
                id = "mode_coder",
                name = "Code Architect",
                subtitle = "Write, refactor, and debug any programming language",
                systemPrompt = "You are a senior software engineer. Provide clean, idiomatic, bug-free code with concise explanations and edge-case awareness.",
                starterPrompts = listOf(
                    "Write a Kotlin coroutine retry helper with exponential backoff",
                    "Debug a slow SQL JOIN query",
                    "Create a REST API endpoint in Python FastAPI"
                ),
                iconKey = "code"
            ),
            AiModePreset(
                id = "mode_summarizer",
                name = "TL;DR Summarizer",
                subtitle = "Distill long notes, articles, or logs into key bullets",
                systemPrompt = "You are an expert editor. Summarize the user's text into a 1-sentence core takeaway followed by concise bullet points and action items.",
                starterPrompts = listOf(
                    "Summarize these meeting notes into action items:",
                    "Ibuod ang mahabang artikulo na ito sa 5 bullets:"
                ),
                iconKey = "summarize"
            ),
            AiModePreset(
                id = "mode_translator",
                name = "Polyglot Translator",
                subtitle = "Context-aware translation (Tagalog, English, Japanese, etc.)",
                systemPrompt = "You are a native-level translator. Translate the user's text accurately while preserving tone, idioms, and formatting. Ask or note nuances if ambiguous.",
                starterPrompts = listOf(
                    "Translate to natural Tagalog: 'Let's touch base tomorrow morning.'",
                    "Translate to formal business English: 'Pasensya na po sa abala, na-delay lang ang delivery.'"
                ),
                iconKey = "translate"
            ),
            AiModePreset(
                id = "mode_brainstorm",
                name = "Idea & Prompt Lab",
                subtitle = "Brainstorm app ideas, content hooks, and business angles",
                systemPrompt = "You are a creative strategist. Generate diverse, practical, and original ideas organized into clear categories with next steps.",
                starterPrompts = listOf(
                    "Magbigay ng 5 unique mobile app ideas para sa small businesses",
                    "Brainstorm YouTube video hooks about AI automation"
                ),
                iconKey = "bulb"
            ),
            AiModePreset(
                id = "mode_analyst",
                name = "Deep Analyst",
                subtitle = "Step-by-step reasoning, pros/cons, and structured analysis",
                systemPrompt = "You are an analytical thinker. Break down complex questions step by step, compare trade-offs objectively, and provide a well-reasoned conclusion.",
                starterPrompts = listOf(
                    "Compare PostgreSQL vs Firestore for a real-time chat app",
                    "Analyze pros and cons of local LLMs vs cloud APIs"
                ),
                iconKey = "analytics"
            )
        )
    }
}
