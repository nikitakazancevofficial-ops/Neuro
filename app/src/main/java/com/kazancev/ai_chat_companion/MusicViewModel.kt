package com.kazancev.ai_chat_companion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazancev.ai_chat_companion.ui.i18n.loadAppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val currentJob: MusicJob? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val sourceAudioUrl: String? = null,
    val sourceAudioName: String? = null,
    val error: String? = null
)

class MusicViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            apiService.getMusicLibrary()
                .onSuccess { tracks -> _uiState.value = _uiState.value.copy(tracks = tracks, isLoading = false) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) }
        }
    }

    fun uploadCoverSource(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            apiService.uploadMusicSource(uri)
                .onSuccess { url ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        sourceAudioUrl = url,
                        sourceAudioName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
                    )
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isUploading = false, error = error.message) }
        }
    }

    fun clearCoverSource() {
        _uiState.value = _uiState.value.copy(sourceAudioUrl = null, sourceAudioName = null)
    }

    fun generate(
        prompt: String,
        cover: Boolean,
        instrumental: Boolean,
        manualMode: Boolean,
        manualStyle: String,
        manualLyrics: String,
        durationMinutes: String
    ) {
        if (!manualMode && prompt.isBlank()) return
        if (manualMode && manualStyle.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Укажите стиль для ACE-Step")
            return
        }
        if (manualMode && !instrumental && manualLyrics.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Добавьте готовый текст песни с куплетами и припевом")
            return
        }
        val source = _uiState.value.sourceAudioUrl
        if (cover && source == null) {
            _uiState.value = _uiState.value.copy(error = "Для кавера выберите исходный аудиофайл")
            return
        }
        val duration = durationMinutes.trim().replace(',', '.').let { value ->
            if (value.isBlank()) null else value.toFloatOrNull()?.times(60f)?.coerceIn(10f, 600f)
        }
        if (durationMinutes.isNotBlank() && duration == null) {
            _uiState.value = _uiState.value.copy(error = "Укажите длительность числом в минутах")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            apiService.createMusic(
                MusicGenerationRequest(
                    userPrompt = if (manualMode) manualStyle.trim() else prompt,
                    taskType = if (cover) "cover" else "text2music",
                    sourceAudioUrl = source,
                    caption = manualStyle.trim().takeIf { manualMode },
                    lyrics = (if (instrumental) "[Instrumental]" else manualLyrics.trim()).takeIf { manualMode },
                    duration = duration,
                    instrumental = instrumental,
                    responseLanguage = loadAppLanguage(apiService.appContext).code
                )
            ).onSuccess { job ->
                _uiState.value = _uiState.value.copy(currentJob = job)
                pollJob(job.id)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun regenerate(track: MusicTrack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            apiService.regenerateMusic(track.id)
                .onSuccess { job ->
                    _uiState.value = _uiState.value.copy(currentJob = job)
                    pollJob(job.id)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    private suspend fun pollJob(jobId: String) {
        while (true) {
            delay(1_000)
            val job = apiService.getMusicJob(jobId).getOrElse { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
                return
            }
            val previous = _uiState.value.currentJob
            val stableJob = if (previous?.id == job.id) {
                job.copy(
                    stage = laterMusicStage(previous.stage, job.stage),
                    progress = maxOf(previous.progress, job.progress)
                )
            } else {
                job
            }
            _uiState.value = _uiState.value.copy(currentJob = stableJob)
            if (job.status == "completed" || job.status == "failed") {
                loadLibrary()
                return
            }
        }
    }

    private fun laterMusicStage(previous: String, current: String): String {
        val order = listOf("queued", "checking_worker", "planning", "submitting", "generating", "aligning_lyrics", "creating_cover", "completed")
        if (current == "failed") return current
        return if (order.indexOf(current) >= order.indexOf(previous)) current else previous
    }
}
