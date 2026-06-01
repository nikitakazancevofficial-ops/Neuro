package com.kazancev.ai_chat_companion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    val error: String? = null
)

@Serializable
data class SimpleResponse(
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class ChatInfo(
    val id: Int,
    val title: String
)

@Serializable
data class ChatSearchResult(
    val id: Int,
    val title: String,
    val snippet: String = ""
)

@Serializable
data class ClientContext(
    @SerialName("date_time")
    val dateTime: String,
    val timezone: String,
    val locale: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_meters")
    val accuracyMeters: Float? = null
)

const val AI_MODE_INSTANT = "instant"
const val AI_MODE_THINKING = "thinking"
const val THINKING_EFFORT_STANDARD = "standard"
const val THINKING_EFFORT_EXTENDED = "extended"

const val MODEL_NEMOTRON_EXTENDED = "nvidia/nemotron-3-nano-omni"
const val MODEL_QWEN_STANDARD = "qwen/qwen3.5-9b"
const val MODEL_GLM_STANDARD = "zai-org/glm-4.6v-flash"
const val MODEL_GEMMA_STANDARD = "google/gemma-4-e4b"
const val MODEL_GEMMA_INSTANT = "google/gemma-3-12b"

const val MODEL_OMNICODER_9B = "omnicoder/omnicoder-9b"

val STANDARD_THINKING_MODELS = listOf(
    MODEL_QWEN_STANDARD,
    MODEL_GLM_STANDARD,
    MODEL_GEMMA_STANDARD,
    MODEL_OMNICODER_9B
)

@Serializable
data class AiSettings(
    val mode: String = AI_MODE_THINKING,
    @SerialName("thinking_effort")
    val thinkingEffort: String = THINKING_EFFORT_STANDARD,
    @SerialName("standard_model")
    val standardModel: String = MODEL_QWEN_STANDARD,
    @SerialName("auto_switch_to_thinking")
    val autoSwitchToThinking: Boolean = true
)

@Serializable
data class ChatRequest(
    val message: String,
    @SerialName("chat_id")
    val chatId: Int? = null,
    val images: List<String> = emptyList(),
    @SerialName("client_context")
    val clientContext: ClientContext? = null,
    val model: String? = null,
    @SerialName("reasoning_mode")
    val reasoningMode: String? = null,
    @SerialName("thinking_effort")
    val thinkingEffort: String? = null,
    @SerialName("response_language")
    val responseLanguage: String = "ru",
    val regenerate: Boolean = false,
    @SerialName("request_id")
    val requestId: String? = null
)

@Serializable
data class ChatResponse(
    val reply: String? = null,
    @SerialName("chat_id")
    val chatId: Int? = null,
    @SerialName("memory_updated")
    val memoryUpdated: String? = null,
    val error: String? = null
)

@Serializable
data class StreamedAiResponse(
    val type: String = "chunk",
    val content: String = "",
    val reasoning: String = "",
    val seq: Int? = null,
    val error: String? = null,
    @SerialName("chat_id")
    val chatId: Int? = null,
    @SerialName("stream_version")
    val streamVersion: String? = null,
    val title: String? = null,
    @SerialName("memory_updated")
    val memoryUpdated: String? = null,
    @SerialName("image_generation")
    val imageGeneration: ImageGenerationInfo? = null
)

@Serializable
data class AiResponse(
    @SerialName("message")
    val message: String? = null,
    @SerialName("chat_id")
    val chatId: Int? = null,
    val error: String? = null
)

@Serializable
data class UploadResponse(
    val url: String
)

@Serializable
data class TranscriptionResponse(
    val text: String,
    val language: String = "ru"
)

@Serializable
data class ServerMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
    @SerialName("image_generation")
    val imageGeneration: ImageGenerationInfo? = null
)

@Serializable
data class ImageGenerationInfo(
    val id: String,
    @SerialName("chat_id")
    val chatId: Int,
    val status: String,
    @SerialName("user_prompt")
    val userPrompt: String,
    val prompt: String = "",
    @SerialName("aspect_ratio")
    val aspectRatio: String = "1:1",
    val width: Int = 768,
    val height: Int = 768,
    val steps: Int = 4,
    @SerialName("guidance_scale")
    val guidanceScale: Float = 1f,
    val seed: Int? = null,
    val url: String? = null,
    val error: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("started_at")
    val startedAt: Long? = null,
    @SerialName("completed_at")
    val completedAt: Long? = null,
    @SerialName("elapsed_seconds")
    val elapsedSeconds: Double? = null,
    val reply: String? = null,
    val attempt: Int = 0,
    @SerialName("max_attempts")
    val maxAttempts: Int = 1,
    @SerialName("review_satisfied")
    val reviewSatisfied: Boolean? = null,
    @SerialName("review_feedback")
    val reviewFeedback: String? = null,
    @SerialName("reference_used")
    val referenceUsed: Boolean = false,
    @SerialName("reference_count")
    val referenceCount: Int = 0
)

@Serializable
data class PersonalizationSettings(
    @SerialName("base_style")
    val baseStyle: String = "Дружелюбный",
    val warmth: String = "По умолчанию",
    val enthusiasm: String = "По умолчанию",
    @SerialName("headings_and_lists")
    val headingsAndLists: String = "По умолчанию",
    val emoji: String = "По умолчанию",
    @SerialName("fast_answers")
    val fastAnswers: Boolean = true,
    @SerialName("custom_instructions")
    val customInstructions: String = ""
)

@Serializable
data class MemorySettings(
    @SerialName("reference_chat_history")
    val referenceChatHistory: Boolean = true,
    @SerialName("use_saved_memory")
    val useSavedMemory: Boolean = true,
    val nickname: String = "",
    val profession: String = "",
    val about: String = ""
)

@Serializable
data class SavedMemory(
    val id: String,
    val text: String,
    @SerialName("created_at")
    val createdAt: Long
)
