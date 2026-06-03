package com.kazancev.ai_chat_companion.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazancev.ai_chat_companion.LyricsLine
import com.kazancev.ai_chat_companion.MusicTrack
import com.kazancev.ai_chat_companion.MusicUiState
import com.kazancev.ai_chat_companion.MusicViewModel
import com.kazancev.ai_chat_companion.ui.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt

private val MusicGradient = Brush.linearGradient(listOf(Color(0xFF5B35D5), Color(0xFF9B6CFF)))
private val PlayerGradient = Brush.verticalGradient(listOf(Color(0xFF24184C), Color(0xFF100A24), Color(0xFF090611)))

@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenTrack: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var coverMode by remember { mutableStateOf(false) }
    var instrumental by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf(false) }
    var manualStyle by remember { mutableStateOf("") }
    var manualLyrics by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadCoverSource(uri)
    }

    LaunchedEffect(Unit) { viewModel.loadLibrary() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppColors.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { MusicHeader(onBack) }
        item { MusicHero() }
        item {
            MusicComposer(
                state = state,
                prompt = prompt,
                coverMode = coverMode,
                instrumental = instrumental,
                manualMode = manualMode,
                manualStyle = manualStyle,
                manualLyrics = manualLyrics,
                durationMinutes = durationMinutes,
                onPromptChange = { prompt = it },
                onModeChange = { coverMode = it },
                onInstrumentalChange = { instrumental = it },
                onManualModeChange = { manualMode = it },
                onManualStyleChange = { manualStyle = it },
                onManualLyricsChange = { manualLyrics = it },
                onDurationMinutesChange = { durationMinutes = it },
                onPickFile = { filePicker.launch("audio/*") },
                onClearFile = viewModel::clearCoverSource,
                onGenerate = {
                    viewModel.generate(prompt, coverMode, instrumental, manualMode, manualStyle, manualLyrics, durationMinutes)
                }
            )
        }
        state.currentJob?.let { job -> item { MusicProgressCard(job.stage, job.progress, job.error) } }
        state.error?.let { error -> item { Text(error, color = AppColors.danger, fontSize = 15.sp) } }
        item {
            Text(tr("Моя музыка"), color = AppColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (state.tracks.isEmpty()) {
            item { EmptyMusicLibrary(state.isLoading) }
        } else {
            items(state.tracks, key = { it.id }) { track ->
                MusicTrackCard(track = track, onClick = { onOpenTrack(track.id) }, onRegenerate = { viewModel.regenerate(track) })
            }
        }
    }
}

@Composable
fun MusicPlayerRoute(viewModel: MusicViewModel, trackId: String, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(trackId) { viewModel.loadLibrary() }
    val track = state.tracks.firstOrNull { it.id == trackId }
    if (track == null) {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.accent)
        }
    } else {
        MusicPlayerScreen(track = track, onBack = onBack, onRegenerate = { viewModel.regenerate(track) })
    }
}

@Composable
private fun MusicHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(onClick = onBack, color = AppColors.softSurface, shape = CircleShape, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(NeuroIcons.Back, contentDescription = tr("Назад"), tint = AppColors.text, modifier = Modifier.size(25.dp))
            }
        }
        Column {
            Text("AI Music", color = AppColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(tr("Локальная музыкальная студия"), color = AppColors.subtleText, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MusicHero() {
    Surface(shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.background(MusicGradient).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Text(tr("Создавайте песни внутри Neuro"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Опишите настроение и тему. Neuro напишет текст, соберёт стиль, создаст музыку и подготовит обложку."),
                color = Color.White.copy(alpha = 0.83f),
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun MusicComposer(
    state: MusicUiState,
    prompt: String,
    coverMode: Boolean,
    instrumental: Boolean,
    manualMode: Boolean,
    manualStyle: String,
    manualLyrics: String,
    durationMinutes: String,
    onPromptChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onInstrumentalChange: (Boolean) -> Unit,
    onManualModeChange: (Boolean) -> Unit,
    onManualStyleChange: (String) -> Unit,
    onManualLyricsChange: (String) -> Unit,
    onDurationMinutesChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onClearFile: () -> Unit,
    onGenerate: () -> Unit
) {
    Surface(color = AppColors.softSurface, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicModeChip(tr("Новая песня"), !coverMode) { onModeChange(false) }
                MusicModeChip(tr("Кавер"), coverMode) { onModeChange(true) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Ручной режим"), color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("Отправить свой стиль и готовый текст напрямую в ACE-Step"), color = AppColors.subtleText, fontSize = 13.sp)
                }
                Switch(
                    checked = manualMode,
                    onCheckedChange = onManualModeChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = AppColors.accent)
                )
            }
            if (manualMode) {
                MusicTextArea(
                    value = manualStyle,
                    onValueChange = onManualStyleChange,
                    placeholder = tr("Стиль для ACE-Step, например: melancholic indie rock, warm male vocal, live drums"),
                    height = 84.dp
                )
                if (!instrumental) {
                    MusicTextArea(
                        value = manualLyrics,
                        onValueChange = onManualLyricsChange,
                        placeholder = tr("[Куплет 1]\nВаш текст...\n\n[Припев]\nВаш припев..."),
                        height = 188.dp
                    )
                }
            } else {
                MusicTextArea(
                    value = prompt,
                    onValueChange = onPromptChange,
                    placeholder = tr("Например: невесёлая песня об Америке с тёплым мужским вокалом"),
                    height = 92.dp
                )
            }
            if (coverMode) {
                CoverSourcePicker(state, onPickFile, onClearFile)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Инструментальная версия"), color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("Создать музыку без вокала"), color = AppColors.subtleText, fontSize = 13.sp)
                }
                Switch(
                    checked = instrumental,
                    onCheckedChange = onInstrumentalChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = AppColors.accent)
                )
            }
            MusicDurationField(durationMinutes, onDurationMinutesChange)
            Button(
                onClick = onGenerate,
                enabled = (
                    if (manualMode) manualStyle.isNotBlank() && (instrumental || manualLyrics.isNotBlank())
                    else prompt.isNotBlank()
                ) && state.currentJob?.status !in setOf("checking_worker", "planning", "submitting", "generating", "aligning_lyrics", "creating_cover"),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(tr(if (coverMode) "Создать кавер" else "Создать песню"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MusicDurationField(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Длительность, необязательно"), color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Surface(color = AppColors.elevated, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    if (next.length <= 5 && next.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(next)
                },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.text, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) Text(tr("Например: 3.5 минуты. Пусто — Neuro выберет сам, максимум 10 минут"), color = AppColors.subtleText, fontSize = 14.sp)
                        inner()
                    }
                }
            )
        }
    }
}

@Composable
private fun MusicTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: androidx.compose.ui.unit.Dp
) {
    Surface(color = AppColors.elevated, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = AppColors.text, fontSize = 17.sp, lineHeight = 23.sp),
            modifier = Modifier.fillMaxWidth().padding(15.dp).height(height),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(placeholder, color = AppColors.subtleText, fontSize = 16.sp, lineHeight = 22.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun MusicModeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) AppColors.accent else AppColors.elevated,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(title, color = if (selected) Color.White else AppColors.text, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CoverSourcePicker(state: MusicUiState, onPickFile: () -> Unit, onClearFile: () -> Unit) {
    Surface(onClick = onPickFile, color = AppColors.elevated, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.sourceAudioName ?: tr("Выберите исходный трек"), color = AppColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr(if (state.isUploading) "Загружаем аудио..." else "MP3, WAV, FLAC, M4A"), color = AppColors.subtleText, fontSize = 13.sp)
            }
            if (state.sourceAudioUrl != null) {
                IconButton(onClick = onClearFile) { Icon(Icons.Outlined.Close, contentDescription = null, tint = AppColors.actionIcon) }
            }
        }
    }
}

@Composable
private fun MusicProgressCard(stage: String, progress: Float, error: String?) {
    Surface(color = AppColors.softSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stage != "completed" && stage != "failed") CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text(musicStageLabel(stage), color = if (stage == "failed") AppColors.danger else AppColors.text, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = AppColors.accent, trackColor = AppColors.divider, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
            if (!error.isNullOrBlank()) Text(error, color = AppColors.danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun musicStageLabel(stage: String): String = tr(
    when (stage) {
        "queued" -> "Ставим задачу в очередь"
        "checking_worker" -> "Проверяем локальный музыкальный движок"
        "planning" -> "Neuro пишет текст и продумывает стиль"
        "submitting" -> "Передаём песню в ACE-Step"
        "generating" -> "Генерируем музыку локально"
        "aligning_lyrics" -> "Синхронизируем текст с вокалом"
        "creating_cover" -> "Создаём обложку для трека"
        "completed" -> "Трек готов"
        "failed" -> "Не удалось создать трек"
        else -> "Готовим музыку"
    }
)

@Composable
private fun EmptyMusicLibrary(loading: Boolean) {
    Surface(color = AppColors.softSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Album, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(38.dp))
            Text(tr(if (loading) "Загружаем библиотеку..." else "Пока здесь нет треков"), color = AppColors.text, fontWeight = FontWeight.SemiBold)
            Text(tr("Первая песня появится здесь после генерации"), color = AppColors.subtleText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MusicTrackCard(track: MusicTrack, onClick: () -> Unit, onRegenerate: () -> Unit) {
    Surface(onClick = onClick, color = AppColors.softSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverArtwork(track.coverUrl, Modifier.size(82.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(track.title, color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.caption, color = AppColors.subtleText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatSeconds(track.duration)}  •  ${track.bpm ?: "—"} BPM", color = AppColors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = AppColors.accent, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White) }
                }
                IconButton(onClick = onRegenerate, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = tr("Перегенерировать"), tint = AppColors.actionIcon)
                }
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(track: MusicTrack, onBack: () -> Unit, onRegenerate: () -> Unit) {
    var player by remember(track.id) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(track.id) { mutableStateOf(false) }
    var prepared by remember(track.id) { mutableStateOf(false) }
    var position by remember(track.id) { mutableFloatStateOf(0f) }
    var duration by remember(track.id) { mutableFloatStateOf(track.duration.coerceAtLeast(1f)) }
    val activeIndex = track.lyricsTimeline.indexOfLast { position >= it.startSeconds }
    val listState = rememberLazyListState()

    DisposableEffect(track.id) {
        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
            setDataSource(track.audioUrl)
            setOnPreparedListener {
                prepared = true
                duration = (it.duration / 1000f).coerceAtLeast(1f)
            }
            setOnCompletionListener { isPlaying = false; position = 0f }
            prepareAsync()
        }
        player = mediaPlayer
        onDispose { mediaPlayer.release(); player = null }
    }

    LaunchedEffect(isPlaying, prepared) {
        while (isPlaying && prepared) {
            position = (player?.currentPosition ?: 0) / 1000f
            delay(250)
        }
    }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Box(modifier = Modifier.fillMaxSize().background(PlayerGradient)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onBack, color = Color.White.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(NeuroIcons.Back, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("AI Music", color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Bold)
                }
            }
            item { CoverArtwork(track.coverUrl, Modifier.fillMaxWidth().aspectRatio(1f)) }
            item {
                Text(track.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(track.caption, color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item {
                Slider(
                    value = position.coerceIn(0f, duration),
                    onValueChange = { position = it; player?.seekTo((it * 1000).roundToInt()) },
                    valueRange = 0f..duration,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Text(formatSeconds(position), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatSeconds(duration), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = {
                            if (!prepared) return@Surface
                            if (isPlaying) player?.pause() else player?.start()
                            isPlaying = !isPlaying
                        },
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null, tint = Color(0xFF25144E), modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                    Surface(onClick = onRegenerate, color = Color.White.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Refresh, contentDescription = tr("Перегенерировать"), tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }
                }
            }
            item {
                Text(tr("Текст песни"), color = Color.White.copy(alpha = 0.62f), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            if (track.lyricsTimeline.isEmpty()) {
                item { Text(tr("Инструментальная композиция"), color = Color.White.copy(alpha = 0.65f), fontSize = 22.sp) }
            } else {
                items(track.lyricsTimeline) { line ->
                    LyricsRow(line = line, active = track.lyricsTimeline.indexOf(line) == activeIndex)
                }
            }
        }
    }
}

@Composable
private fun LyricsRow(line: LyricsLine, active: Boolean) {
    Column(modifier = Modifier.padding(vertical = if (active) 8.dp else 3.dp)) {
        if (line.section.isNotBlank()) Text(line.section.uppercase(), color = Color(0xFFB597FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            line.text,
            color = if (active) Color.White else Color.White.copy(alpha = 0.34f),
            fontSize = if (active) 25.sp else 21.sp,
            lineHeight = if (active) 31.sp else 27.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CoverArtwork(url: String?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            if (url.isNullOrBlank()) null else runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MusicGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}

private fun formatSeconds(seconds: Float): String {
    val safe = seconds.coerceAtLeast(0f).roundToInt()
    return "%d:%02d".format(safe / 60, safe % 60)
}
