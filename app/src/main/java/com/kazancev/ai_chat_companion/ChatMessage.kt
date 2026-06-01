package com.kazancev.ai_chat_companion

data class ChatMessage(
    val text: String = "",
    val author: Author = Author.USER,
    val imageUri: String? = null,
    val isLoading: Boolean = false,
    val reasoning: String = "",
    val memoryUpdated: String? = null,
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingStartedAt: Long? = null,
    val thinkingFinishedAt: Long? = null,
    val imageGeneration: ImageGenerationInfo? = null
)

enum class Author {
    USER,
    AI
}
