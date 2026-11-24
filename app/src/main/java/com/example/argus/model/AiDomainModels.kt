package com.example.argus.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderProtocol(val label: String, val requiresKey: Boolean) {
    FIREBASE_GEMINI("Built-in Gemini (Firebase AI Logic)", false),
    OPENAI_COMPATIBLE("OpenAI-Compatible API", true),
    ANTHROPIC("Anthropic Messages API", true),
    GEMINI_REST("Gemini REST (User API Key)", true)
}

@Serializable
data class AiProviderProfile(
    val id: String,
    val name: String,
    val protocol: ProviderProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val modelId: String,
    val presetModels: List<String> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val isCustom: Boolean = false
)

@Serializable
data class AiModePreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val systemPrompt: String,
    val starterPrompts: List<String> = emptyList(),
    val iconKey: String = "spark",
    val isCustom: Boolean = false
)

@Serializable
enum class MessageRole {
    USER, MODEL, ERROR
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestamp: String,
    val modelBadge: String? = null
)

@Serializable
data class RunSessionRecord(
    val id: String,
    val title: String,
    val modeId: String,
    val modeName: String,
    val providerName: String,
    val modelId: String,
    val updatedAt: String,
    val messages: List<ChatMessage>
)
