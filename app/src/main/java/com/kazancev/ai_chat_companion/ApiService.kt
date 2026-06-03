package com.kazancev.ai_chat_companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.kazancev.ai_chat_companion.ui.i18n.loadAppLanguage

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val TOKEN_KEY = stringPreferencesKey("jwt_token")
private val AI_MODE_KEY = stringPreferencesKey("ai_mode")
private val THINKING_EFFORT_KEY = stringPreferencesKey("thinking_effort")
private val STANDARD_MODEL_KEY = stringPreferencesKey("standard_model")
private val AUTO_SWITCH_THINKING_KEY = booleanPreferencesKey("auto_switch_to_thinking")
private val SERVER_URL_KEY = stringPreferencesKey("server_url")

private data class AiRoute(
    val model: String,
    val mode: String,
    val effort: String
)

@OptIn(ExperimentalSerializationApi::class)
class ApiService(val appContext: Context) {
    private val context = appContext
    private val serverCandidates = listOf(
        BuildConfig.NEURO_SERVER_URL,
        "http://10.0.2.2:3510",
        "http://127.0.0.1:3510"
    ).distinct()

    @Volatile
    private var activeServerUrl: String = serverCandidates.first()

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val aiSettings: Flow<AiSettings> = context.dataStore.data.map { preferences ->
        val standardModel = preferences[STANDARD_MODEL_KEY]
            ?.takeIf { it in STANDARD_THINKING_MODELS }
            ?: MODEL_QWEN_STANDARD
        AiSettings(
            mode = preferences[AI_MODE_KEY].takeIf { it == AI_MODE_INSTANT || it == AI_MODE_THINKING }
                ?: AI_MODE_THINKING,
            thinkingEffort = preferences[THINKING_EFFORT_KEY]
                .takeIf { it == THINKING_EFFORT_STANDARD || it == THINKING_EFFORT_EXTENDED }
                ?: THINKING_EFFORT_STANDARD,
            standardModel = standardModel,
            autoSwitchToThinking = preferences[AUTO_SWITCH_THINKING_KEY] ?: true
        )
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 86_400_000
            socketTimeoutMillis = 86_400_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences -> preferences[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences -> preferences.remove(TOKEN_KEY) }
    }

    suspend fun hasToken(): Boolean {
        return context.dataStore.data.map { it[TOKEN_KEY] }.first() != null
    }

    suspend fun updateAiMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODE_KEY] = if (mode == AI_MODE_INSTANT) AI_MODE_INSTANT else AI_MODE_THINKING
        }
    }

    suspend fun updateThinkingEffort(effort: String) {
        context.dataStore.edit { preferences ->
            preferences[THINKING_EFFORT_KEY] =
                if (effort == THINKING_EFFORT_EXTENDED) THINKING_EFFORT_EXTENDED else THINKING_EFFORT_STANDARD
            preferences[AI_MODE_KEY] = AI_MODE_THINKING
        }
    }

    suspend fun updateStandardModel(model: String) {
        context.dataStore.edit { preferences ->
            // ← Добавил MODEL_OMNICODER_9B в список разрешённых моделей
            val allowedModels = listOf(
                MODEL_QWEN_STANDARD,
                MODEL_GLM_STANDARD,
                MODEL_GEMMA_STANDARD,
                MODEL_OMNICODER_9B  // ← Добавил новую модель
            )

            preferences[STANDARD_MODEL_KEY] = model.takeIf { it in allowedModels } ?: MODEL_QWEN_STANDARD
            preferences[AI_MODE_KEY] = AI_MODE_THINKING
            preferences[THINKING_EFFORT_KEY] = THINKING_EFFORT_STANDARD
        }
    }

    suspend fun updateAutoSwitchToThinking(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_SWITCH_THINKING_KEY] = enabled }
    }

    suspend fun getConfiguredServerUrl(): String {
        return context.dataStore.data.map { preferences ->
            preferences[SERVER_URL_KEY] ?: BuildConfig.NEURO_SERVER_URL
        }.first()
    }

    suspend fun checkAndSaveServerUrl(value: String): Result<String> {
        return runCatching {
            val normalized = normalizeServerUrl(value)
            val response = client.get("$normalized/health")
            if (!response.status.isSuccess()) {
                error("Сервер ответил HTTP ${response.status.value}")
            }
            context.dataStore.edit { preferences -> preferences[SERVER_URL_KEY] = normalized }
            activeServerUrl = normalized
            normalized
        }
    }

    suspend fun createChat(): Result<ChatInfo> {
        return runRequest { baseUrl -> client.post("$baseUrl/chats").body() }
    }

    fun streamChat(
        userInput: String,
        chatId: Int,
        images: List<String> = emptyList(),
        regenerate: Boolean = false
    ): Flow<StreamedAiResponse> {
        return flow {
            var lastError: Throwable? = null
            val route = resolveAiRoute(userInput, images)
            val requestId = UUID.randomUUID().toString()

            for (baseUrl in orderedServerCandidates()) {
                try {
                    suspend fun emitEvent(rawEvent: String): Boolean {
                        val data = rawEvent
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.startsWith("data:") }
                            .joinToString(separator = "\n") { it.removePrefix("data:").trim() }

                        if (data == "[DONE]") return true
                        if (data.isEmpty()) return false

                        try {
                            emit(json.decodeFromString<StreamedAiResponse>(data))
                        } catch (e: Exception) {
                            println("SSE parse error: ${e.message}; data=${data.take(240)}")
                        }
                        return false
                    }

                    val streamed = client.preparePost("$baseUrl/chat/stream") {
                        contentType(ContentType.Application.Json)
                        header("Accept", "text/event-stream")
                        header("Cache-Control", "no-cache")
                        header("Connection", "keep-alive")
                        setBody(
                            ChatRequest(
                                message = userInput,
                                chatId = chatId,
                                images = images,
                                clientContext = buildClientContext(),
                                model = route.model,
                                reasoningMode = route.mode,
                                thinkingEffort = route.effort,
                                responseLanguage = loadAppLanguage(context).code,
                                regenerate = regenerate,
                                requestId = requestId
                            )
                        )
                    }.execute { response: HttpResponse ->
                        if (!response.status.isSuccess()) {
                            lastError = IllegalStateException("HTTP ${response.status.value} from $baseUrl")
                            return@execute false
                        }

                        activeServerUrl = baseUrl
                        val channel: ByteReadChannel = response.bodyAsChannel()
                        val packet = ByteArray(8 * 1024)
                        var buffer = ""

                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(packet, 0, packet.size)
                            if (bytesRead == -1) break
                            if (bytesRead <= 0) continue

                            buffer += String(packet, 0, bytesRead, Charsets.UTF_8)
                                .replace("\r\n", "\n")
                                .replace("\r", "\n")

                            while (buffer.contains("\n\n")) {
                                val separatorIndex = buffer.indexOf("\n\n")
                                val rawEvent = buffer.substring(0, separatorIndex)
                                buffer = buffer.substring(separatorIndex + 2)
                                if (emitEvent(rawEvent)) return@execute true
                            }
                        }

                        if (buffer.isNotBlank()) emitEvent(buffer)
                        true
                    }

                    if (streamed) return@flow
                } catch (e: Exception) {
                    lastError = e
                }
            }

            emit(
                StreamedAiResponse(
                    type = "error",
                    error = connectionError(lastError)
                )
            )
        }.catch { e ->
            emit(StreamedAiResponse(type = "error", error = "Streaming error: ${e.message}"))
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(userInput: String, chatId: Int?, images: List<String> = emptyList()): Result<ChatResponse> {
        val route = resolveAiRoute(userInput, images)
        return runRequest { baseUrl ->
            client.post("$baseUrl/chat") {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        message = userInput,
                        chatId = chatId,
                        images = images,
                        clientContext = buildClientContext(),
                        model = route.model,
                        reasoningMode = route.mode,
                        thinkingEffort = route.effort,
                        responseLanguage = loadAppLanguage(context).code,
                        requestId = UUID.randomUUID().toString()
                    )
                )
            }.body()
        }
    }

    suspend fun login(loginRequest: LoginRequest): Result<AuthResponse> {
        return runRequest { baseUrl ->
            client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(loginRequest)
            }.body()
        }
    }

    suspend fun register(registerRequest: RegisterRequest): Result<SimpleResponse> {
        return runRequest { baseUrl ->
            client.post("$baseUrl/register") {
                contentType(ContentType.Application.Json)
                setBody(registerRequest)
            }.body()
        }
    }

    suspend fun getChats(): Result<List<ChatInfo>> {
        return runRequest { baseUrl -> client.get("$baseUrl/chats").body() }
    }

    suspend fun searchChats(query: String): Result<List<ChatSearchResult>> {
        val clean = query.trim()
        if (clean.isBlank()) return Result.success(emptyList())
        return runRequest { baseUrl ->
            client.get("$baseUrl/chats/search") {
                url { parameters.append("q", clean) }
            }.body()
        }
    }

    suspend fun getMessages(chatId: Int): Result<List<ServerMessage>> {
        return runRequest { baseUrl -> client.get("$baseUrl/chats/$chatId/messages").body() }
    }

    suspend fun getImageGenerationJob(jobId: String): Result<ImageGenerationInfo> {
        return runRequest { baseUrl -> client.get("$baseUrl/images/jobs/$jobId").body() }
    }

    suspend fun getMusicLibrary(): Result<List<MusicTrack>> {
        return runRequest { baseUrl -> client.get("$baseUrl/music/library").body() }
    }

    suspend fun getMusicTrack(trackId: String): Result<MusicTrack> {
        return runRequest { baseUrl -> client.get("$baseUrl/music/library/$trackId").body() }
    }

    suspend fun createMusic(request: MusicGenerationRequest): Result<MusicJob> {
        return runRequest { baseUrl ->
            client.post("$baseUrl/music/${if (request.taskType == "cover") "cover" else "generate"}") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    suspend fun regenerateMusic(trackId: String): Result<MusicJob> {
        return runRequest { baseUrl ->
            client.post("$baseUrl/music/library/$trackId/regenerate").body()
        }
    }

    suspend fun getMusicJob(jobId: String): Result<MusicJob> {
        return runRequest { baseUrl -> client.get("$baseUrl/music/jobs/$jobId").body() }
    }

    suspend fun uploadMusicSource(uri: Uri): Result<String> {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read audio file")
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "cover.mp3"
            runRequest { baseUrl ->
                client.submitFormWithBinaryData(
                    url = "$baseUrl/music/uploads",
                    formData = formData {
                        append(
                            "file",
                            bytes,
                            headersOf(
                                "Content-Disposition" to listOf("form-data; name=\"file\"; filename=\"$fileName\""),
                                "Content-Type" to listOf(mime)
                            )
                        )
                    }
                ).body<UploadResponse>()
            }.getOrThrow().url
        }
    }

    suspend fun getPersonalization(): Result<PersonalizationSettings> {
        return runRequest { baseUrl -> client.get("$baseUrl/settings/personalization").body() }
    }

    suspend fun updatePersonalization(settings: PersonalizationSettings): Result<PersonalizationSettings> {
        return runRequest { baseUrl ->
            client.put("$baseUrl/settings/personalization") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
        }
    }

    suspend fun getMemorySettings(): Result<MemorySettings> {
        return runRequest { baseUrl -> client.get("$baseUrl/settings/memory").body() }
    }

    suspend fun updateMemorySettings(settings: MemorySettings): Result<MemorySettings> {
        return runRequest { baseUrl ->
            client.put("$baseUrl/settings/memory") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }.body()
        }
    }

    suspend fun getSavedMemories(): Result<List<SavedMemory>> {
        return runRequest { baseUrl -> client.get("$baseUrl/memories").body() }
    }

    suspend fun deleteSavedMemory(id: String): Result<SimpleResponse> {
        return runRequest { baseUrl -> client.delete("$baseUrl/memories/$id").body() }
    }

    suspend fun uploadImage(uri: Uri): Result<String> {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot read image"))
            val bytes = inputStream.use { it.readBytes() }

            val response = runRequest { baseUrl ->
                client.submitFormWithBinaryData(
                    url = "$baseUrl/upload",
                    formData = formData {
                        append(
                            "file",
                            bytes,
                            headersOf(
                                "Content-Disposition" to listOf("form-data; name=\"file\"; filename=\"upload.jpg\""),
                                "Content-Type" to listOf("image/jpeg")
                            )
                        )
                    }
                ).body<UploadResponse>()
            }

            response.map { it.url }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun transcribeAudio(file: File): Result<String> {
        return runCatching {
            val response = runRequest { baseUrl ->
                client.submitFormWithBinaryData(
                    url = "$baseUrl/transcribe",
                    formData = formData {
                        append(
                            "file",
                            file.readBytes(),
                            headersOf(
                                "Content-Disposition" to listOf("form-data; name=\"file\"; filename=\"${file.name}\""),
                                "Content-Type" to listOf("audio/mp4")
                            )
                        )
                    }
                ).body<TranscriptionResponse>()
            }

            response.map { it.text }
        }.getOrElse { Result.failure(it) }
    }

    fun getBaseUrl(): String = activeServerUrl

    fun shareChatUrl(chatId: Int): String = "${activeServerUrl}/share/chats/$chatId"

    private suspend fun resolveAiRoute(userInput: String, images: List<String>): AiRoute {
        val settings = aiSettings.first()
        val shouldAutoThink = settings.mode == AI_MODE_INSTANT &&
                settings.autoSwitchToThinking &&
                shouldSwitchInstantToThinking(userInput, images)

        return when {
            settings.mode == AI_MODE_INSTANT && !shouldAutoThink -> AiRoute(
                model = MODEL_GEMMA_INSTANT,
                mode = AI_MODE_INSTANT,
                effort = THINKING_EFFORT_STANDARD
            )

            // ← Добавил проверку на omnicoder-9b для extended thinking
            settings.thinkingEffort == THINKING_EFFORT_EXTENDED &&
                    settings.standardModel == MODEL_OMNICODER_9B -> AiRoute(
                model = MODEL_OMNICODER_9B,
                mode = AI_MODE_THINKING,
                effort = THINKING_EFFORT_EXTENDED
            )

            settings.thinkingEffort == THINKING_EFFORT_EXTENDED -> AiRoute(
                model = MODEL_NEMOTRON_EXTENDED,
                mode = AI_MODE_THINKING,
                effort = THINKING_EFFORT_EXTENDED
            )

            else -> AiRoute(
                model = settings.standardModel.takeIf { it in STANDARD_THINKING_MODELS } ?: MODEL_QWEN_STANDARD,
                mode = AI_MODE_THINKING,
                effort = THINKING_EFFORT_STANDARD
            )
        }
    }

    private fun shouldSwitchInstantToThinking(userInput: String, images: List<String>): Boolean {
        if (images.isNotEmpty()) return true
        val text = userInput.lowercase(Locale.getDefault())
        if (text.length >= 220) return true
        val hardMarkers = listOf("проанализ", "объясни подробно", "почему", "код", "ошибка", "архитектур", "сравни", "план", "рассужд", "сложн")
        return hardMarkers.any { it in text }
    }

    private fun buildClientContext(): ClientContext {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timezone = TimeZone.getDefault()
        val location = latestKnownLocationOrNull()
        return ClientContext(
            dateTime = formatter.format(Date()),
            timezone = timezone.id,
            locale = Locale.getDefault().toLanguageTag(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy
        )
    }

    private fun latestKnownLocationOrNull(): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            manager.getProviders(true)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    private suspend fun orderedServerCandidates(): List<String> {
        val configuredServerUrl = getConfiguredServerUrl()
        return listOf(activeServerUrl, configuredServerUrl, *serverCandidates.toTypedArray()).distinct()
    }

    private suspend fun <T> runRequest(block: suspend (String) -> T): Result<T> {
        var lastError: Throwable? = null

        for (baseUrl in orderedServerCandidates()) {
            try {
                val result = block(baseUrl)
                activeServerUrl = baseUrl
                return Result.success(result)
            } catch (e: Exception) {
                lastError = e
                println("Server request failed at $baseUrl: ${e.message}")
            }
        }

        return Result.failure(
            IllegalStateException(
                connectionError(lastError)
            )
        )
    }

    private fun normalizeServerUrl(value: String): String {
        val clean = value.trim().trimEnd('/')
        require(clean.isNotBlank()) { "Введите адрес сервера из консоли" }
        val withScheme = if ("://" in clean) clean else "http://$clean"
        require(withScheme.startsWith("http://") || withScheme.startsWith("https://")) {
            "Адрес должен начинаться с http:// или https://"
        }
        return withScheme
    }

    private fun connectionError(lastError: Throwable?): String {
        return buildString {
            append("Не удалось подключиться к Neuro Server. ")
            append("Проверьте, что ПК и телефон находятся в одной сети и сервер запущен. ")
            append("Откройте консоль run_server.bat, скопируйте адрес из блока «Введите в приложении» ")
            append("и укажите его в настройке «Подключение к ПК».")
            lastError?.message?.takeIf { it.isNotBlank() }?.let { append("\n\nПоследняя ошибка: $it") }
        }
    }
}
