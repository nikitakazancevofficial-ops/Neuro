package com.kazancev.ai_chat_companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File

sealed class ChatEvent {
    object RefreshChatList : ChatEvent()
}

private data class ParsedAssistantText(
    val visible: String,
    val reasoning: String
)

class ChatViewModel(private val apiService: ApiService) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatInfo>>(emptyList())
    val chats: StateFlow<List<ChatInfo>> = _chats.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ChatSearchResult>> = _searchResults.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events = _events.asSharedFlow()

    private val _currentChatId = MutableStateFlow<Int?>(null)
    val currentChatId: StateFlow<Int?> = _currentChatId.asStateFlow()

    val aiSettings: StateFlow<AiSettings> = apiService.aiSettings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AiSettings()
    )

    private var streamJob: Job? = null
    private var revealJob: Job? = null
    private var searchJob: Job? = null
    private val imagePollingJobs = mutableMapOf<String, Job>()

    fun loadChats() {
        viewModelScope.launch {
            apiService.getChats()
                .onSuccess { _chats.value = it }
                .onFailure { println("Chat list loading error: ${it.message}") }
        }
    }

    fun loadChat(chatId: Int?) {
        if (chatId == _currentChatId.value && chatId != null) return

        stopGeneration()
        stopImagePolling()
        _currentChatId.value = chatId
        if (chatId == null) {
            _messages.value = emptyList()
            return
        }

        viewModelScope.launch {
            apiService.getMessages(chatId).onSuccess { serverMessages ->
                _messages.value = serverMessages.map { serverMsg ->
                    if (serverMsg.role == "user") {
                        ChatMessage(
                            text = serverMsg.content,
                            author = Author.USER,
                            imageUri = serverMsg.images.firstOrNull()
                        )
                    } else {
                        val parsed = parseAssistantText(serverMsg.content)
                        ChatMessage(
                            text = parsed.visible,
                            reasoning = parsed.reasoning,
                            author = Author.AI,
                            imageGeneration = serverMsg.imageGeneration,
                            thinkingStartedAt = serverMsg.imageGeneration?.startedAt?.times(1000),
                            thinkingFinishedAt = serverMsg.imageGeneration?.completedAt?.times(1000)
                        )
                    }
                }
                serverMessages.mapNotNull { it.imageGeneration }
                    .filter { it.status == "planning" || it.status == "generating" || it.status == "reviewing" }
                    .forEach(::startImagePolling)
            }.onFailure {
                _messages.value = listOf(ChatMessage(text = "Не удалось загрузить чат: ${it.message}", author = Author.AI))
            }
        }
    }

    fun startNewLocalChat(): Boolean {
        if (_currentChatId.value == null && _messages.value.isEmpty()) return false

        stopGeneration()
        stopImagePolling()
        _currentChatId.value = null
        _messages.value = emptyList()
        return true
    }

    fun createChat(onCreated: (Int) -> Unit) {
        createNewChat(onCreated)
    }

    fun createNewChat(onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            apiService.createChat().onSuccess { chat ->
                stopImagePolling()
                _currentChatId.value = chat.id
                _messages.value = emptyList()
                loadChats()
                onCreated(chat.id)
            }.onFailure { error ->
                println("Chat creation error: ${error.message}")
                _currentChatId.value = null
                _messages.value = emptyList()
                onCreated(-1)
            }
        }
    }

    fun searchChats(query: String) {
        searchJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(250)
            apiService.searchChats(clean)
                .onSuccess { _searchResults.value = it }
                .onFailure {
                    println("Chat search error: ${it.message}")
                    _searchResults.value = emptyList()
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
    }

    fun setAiMode(mode: String) {
        viewModelScope.launch { apiService.updateAiMode(mode) }
    }

    fun setThinkingEffort(effort: String) {
        viewModelScope.launch { apiService.updateThinkingEffort(effort) }
    }

    fun setStandardModel(model: String) {
        viewModelScope.launch { apiService.updateStandardModel(model) }
    }

    fun setAutoSwitchToThinking(enabled: Boolean) {
        viewModelScope.launch { apiService.updateAutoSwitchToThinking(enabled) }
    }

    fun sendMessage(
        userInput: String,
        imageUri: String? = null,
        appendUserMessage: Boolean = true,
        regenerate: Boolean = false
    ) {
        val prompt = userInput.trim()
        if ((prompt.isBlank() && imageUri == null) || _isGenerating.value) return

        streamJob = viewModelScope.launch {
            revealJob?.cancel()

            val chatId = ensureCurrentChat().getOrElse { error ->
                _messages.update {
                    it + ChatMessage(
                        text = "Не удалось подключиться к серверу.\n\n${error.message ?: "Проверьте Wi-Fi и запущен ли run_server.bat."}",
                        author = Author.AI
                    )
                }
                return@launch
            }

            val startedAt = System.currentTimeMillis()
            _isGenerating.value = true
            _messages.update {
                val withUserMessage = if (appendUserMessage) {
                    it + ChatMessage(prompt, Author.USER, imageUri = imageUri)
                } else {
                    it
                }
                withUserMessage + ChatMessage(
                        text = "",
                        author = Author.AI,
                        isLoading = true,
                        isStreaming = true,
                        isThinking = true,
                        thinkingStartedAt = startedAt
                    )
            }

            val imageUrls = if (imageUri != null) {
                try {
                    val url = apiService.uploadImage(android.net.Uri.parse(imageUri)).getOrElse { error ->
                        _isGenerating.value = false
                        replaceLastAssistantWithError("Не удалось загрузить фото на сервер: ${error.message}", startedAt)
                        return@launch
                    }
                    listOf(url)
                } catch (error: Exception) {
                    _isGenerating.value = false
                    replaceLastAssistantWithError("Не удалось прочитать фото: ${error.message}", startedAt)
                    return@launch
                }
            } else {
                emptyList()
            }

            val visibleAnswer = StringBuilder()
            var streamedReasoning = ""
            var memoryUpdated: String? = null
            var imageGeneration: ImageGenerationInfo? = null
            var thinkingFinishedAt: Long? = null
            var streamFinished = false

            fun pushAssistant(
                visible: String = visibleAnswer.toString(),
                isThinking: Boolean = visible.isBlank() && !streamFinished,
                isStreaming: Boolean = !streamFinished
            ) {
                updateLastAssistant(
                    visible = visible,
                    reasoning = streamedReasoning,
                    memoryUpdated = memoryUpdated,
                    isStreaming = isStreaming,
                    isThinking = isThinking,
                    thinkingStartedAt = startedAt,
                    thinkingFinishedAt = thinkingFinishedAt,
                    imageGeneration = imageGeneration
                )
            }

            try {
                apiService.streamChat(prompt, chatId, imageUrls, regenerate).collect { event ->
                    when (event.type) {
                        "start" -> event.chatId?.let { _currentChatId.value = it }
                        "reasoning" -> {
                            streamedReasoning += event.reasoning.ifBlank { event.content }
                            pushAssistant(isThinking = visibleAnswer.isBlank())
                            yield()
                        }
                        "chunk" -> {
                            if (thinkingFinishedAt == null) thinkingFinishedAt = System.currentTimeMillis()
                            visibleAnswer.append(event.content)
                            pushAssistant(isThinking = false, isStreaming = true)
                            yield()
                        }
                        "memory_updated" -> {
                            memoryUpdated = event.memoryUpdated ?: event.content
                            pushAssistant()
                        }
                        "image_generation" -> {
                            imageGeneration = event.imageGeneration
                            thinkingFinishedAt = thinkingFinishedAt ?: System.currentTimeMillis()
                            pushAssistant(isThinking = false, isStreaming = false)
                            event.imageGeneration?.let(::startImagePolling)
                        }
                        "done" -> {
                            thinkingFinishedAt = thinkingFinishedAt ?: System.currentTimeMillis()
                            event.chatId?.let { _currentChatId.value = it }
                            streamFinished = true
                            pushAssistant(isThinking = false, isStreaming = false)
                            _events.emit(ChatEvent.RefreshChatList)
                            loadChats()
                        }
                        "error" -> {
                            thinkingFinishedAt = thinkingFinishedAt ?: System.currentTimeMillis()
                            visibleAnswer.clear()
                            visibleAnswer.append(event.error ?: "Ошибка стриминга")
                            streamFinished = true
                            pushAssistant(isThinking = false, isStreaming = false)
                        }
                        else -> {
                            if (event.content.isNotEmpty()) {
                                if (thinkingFinishedAt == null) thinkingFinishedAt = System.currentTimeMillis()
                                visibleAnswer.append(event.content)
                                pushAssistant(isThinking = false, isStreaming = true)
                                yield()
                            }
                        }
                    }
                }
                streamFinished = true
                pushAssistant(isThinking = false, isStreaming = false)
            } catch (e: Exception) {
                if (streamJob?.isCancelled == true) return@launch
                thinkingFinishedAt = thinkingFinishedAt ?: System.currentTimeMillis()
                visibleAnswer.clear()
                visibleAnswer.append("Ошибка: ${e.message ?: "поток прерван"}")
                streamFinished = true
                pushAssistant(isThinking = false, isStreaming = false)
            } finally {
                _isGenerating.value = false
                thinkingFinishedAt = thinkingFinishedAt ?: System.currentTimeMillis()
                if (visibleAnswer.isNotBlank() || streamedReasoning.isNotBlank()) {
                    pushAssistant(isThinking = false, isStreaming = false)
                }
                revealJob = null
            }
        }
    }

    fun stopGeneration() {
        streamJob?.cancel()
        revealJob?.cancel()
        streamJob = null
        revealJob = null
        _isGenerating.value = false
        _messages.update { messages ->
            val mutable = messages.toMutableList()
            val index = mutable.indexOfLast { it.author == Author.AI && (it.isStreaming || it.isThinking) }
            if (index >= 0) {
                val msg = mutable[index]
                mutable[index] = msg.copy(
                    isLoading = false,
                    isStreaming = false,
                    isThinking = false,
                    thinkingFinishedAt = msg.thinkingFinishedAt ?: System.currentTimeMillis()
                )
            }
            mutable.toList()
        }
    }

    fun regenerateLastResponse() {
        if (_isGenerating.value) return
        val messages = _messages.value
        val lastAssistant = messages.indexOfLast { it.author == Author.AI }
        val lastUser = messages.indexOfLast { it.author == Author.USER }
        if (lastUser < 0) return

        val userMessage = messages[lastUser]
        _messages.value = messages.filterIndexed { index, _ ->
            index != lastAssistant && !(lastAssistant < 0 && index == lastUser)
        }
        sendMessage(
            userInput = userMessage.text,
            imageUri = userMessage.imageUri,
            appendUserMessage = false,
            regenerate = true
        )
    }

    fun shareCurrentChatUrl(): String? {
        val chatId = _currentChatId.value ?: return null
        return apiService.shareChatUrl(chatId)
    }

    suspend fun transcribeAudio(file: File): Result<String> {
        return apiService.transcribeAudio(file)
    }

    fun sendMessageNonStreaming(userInput: String, imageDataUrls: List<String> = emptyList()) {
        val prompt = userInput.trim()
        if (prompt.isBlank() && imageDataUrls.isEmpty()) return

        _messages.update { it + ChatMessage(prompt, Author.USER) }

        viewModelScope.launch {
            val chatId = ensureCurrentChat().getOrNull()
            apiService.sendMessage(prompt, chatId, imageDataUrls).onSuccess { response ->
                val parsed = parseAssistantText(response.reply.orEmpty())
                _messages.update {
                    it + ChatMessage(
                        text = parsed.visible.ifBlank { "Пустой ответ" },
                        reasoning = parsed.reasoning,
                        memoryUpdated = response.memoryUpdated,
                        author = Author.AI
                    )
                }
                response.chatId?.let { _currentChatId.value = it }
                _events.emit(ChatEvent.RefreshChatList)
            }.onFailure { error ->
                _messages.update { it + ChatMessage("Ошибка: ${error.message}", Author.AI) }
            }
        }
    }

    private suspend fun ensureCurrentChat(): Result<Int> {
        _currentChatId.value?.takeIf { it > 0 }?.let { return Result.success(it) }

        return apiService.createChat().map { chat ->
            _currentChatId.value = chat.id
            _events.emit(ChatEvent.RefreshChatList)
            loadChats()
            chat.id
        }
    }

    private fun updateLastAssistant(
        visible: String,
        reasoning: String,
        memoryUpdated: String? = null,
        isStreaming: Boolean,
        isThinking: Boolean,
        thinkingStartedAt: Long?,
        thinkingFinishedAt: Long?,
        imageGeneration: ImageGenerationInfo? = null
    ) {
        val parsed = parseAssistantText(visible)
        val combinedReasoning = listOf(reasoning, parsed.reasoning)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")

        _messages.update { messages ->
            val mutable = messages.toMutableList()
            val lastAssistantIndex = mutable.indexOfLast { it.author == Author.AI }
            val updated = ChatMessage(
                text = parsed.visible,
                author = Author.AI,
                isLoading = parsed.visible.isBlank() && combinedReasoning.isBlank() && isStreaming,
                reasoning = combinedReasoning,
                memoryUpdated = memoryUpdated,
                isStreaming = isStreaming,
                isThinking = isThinking || (parsed.visible.isBlank() && isStreaming),
                thinkingStartedAt = thinkingStartedAt,
                thinkingFinishedAt = thinkingFinishedAt,
                imageGeneration = imageGeneration
            )

            if (lastAssistantIndex >= 0) mutable[lastAssistantIndex] = updated else mutable.add(updated)
            mutable.toList()
        }
    }

    private fun replaceLastAssistantWithError(message: String, startedAt: Long) {
        _messages.update { messages ->
            val mutable = messages.toMutableList()
            val lastAssistantIndex = mutable.indexOfLast { it.author == Author.AI }
            val updated = ChatMessage(
                text = message,
                author = Author.AI,
                isLoading = false,
                isStreaming = false,
                isThinking = false,
                thinkingStartedAt = startedAt,
                thinkingFinishedAt = System.currentTimeMillis()
            )
            if (lastAssistantIndex >= 0) mutable[lastAssistantIndex] = updated else mutable.add(updated)
            mutable.toList()
        }
    }

    private fun startImagePolling(imageGeneration: ImageGenerationInfo) {
        if (
            imageGeneration.status != "planning" &&
            imageGeneration.status != "generating" &&
            imageGeneration.status != "reviewing"
        ) return
        if (imagePollingJobs[imageGeneration.id]?.isActive == true) return

        imagePollingJobs[imageGeneration.id] = viewModelScope.launch {
            try {
                while (true) {
                    delay(1_600)
                    val latest = apiService.getImageGenerationJob(imageGeneration.id).getOrNull() ?: continue
                    updateImageGeneration(latest)
                    if (latest.status == "completed" || latest.status == "failed") break
                }
            } finally {
                imagePollingJobs.remove(imageGeneration.id)
            }
        }
    }

    private fun stopImagePolling() {
        imagePollingJobs.values.forEach { it.cancel() }
        imagePollingJobs.clear()
    }

    private fun updateImageGeneration(imageGeneration: ImageGenerationInfo) {
        _messages.update { messages ->
            messages.map { message ->
                if (message.imageGeneration?.id == imageGeneration.id) {
                    message.copy(
                        imageGeneration = imageGeneration,
                        isLoading = false,
                        isStreaming = false,
                        isThinking = false,
                        thinkingStartedAt = imageGeneration.startedAt?.times(1000) ?: message.thinkingStartedAt,
                        thinkingFinishedAt = imageGeneration.completedAt?.times(1000) ?: message.thinkingFinishedAt
                    )
                } else {
                    message
                }
            }
        }
    }

    private fun parseAssistantText(raw: String): ParsedAssistantText {
        if (raw.isBlank()) return ParsedAssistantText("", "")

        val tagRegex = Regex(
            pattern = """<\s*(think|thinking|reasoning|analysis|thought|reflection)\s*>([\s\S]*?)(?:<\s*/\s*\1\s*>|$)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val reasoning = StringBuilder()
        val visible = StringBuilder()
        var lastEnd = 0

        for (match in tagRegex.findAll(raw)) {
            visible.append(raw.substring(lastEnd, match.range.first))
            reasoning.append(match.groupValues.getOrNull(2).orEmpty().trim())
            reasoning.append("\n\n")
            lastEnd = match.range.last + 1
        }

        visible.append(raw.substring(lastEnd))
        return ParsedAssistantText(
            visible = visible.toString().trim(),
            reasoning = reasoning.toString().trim()
        )
    }
}
