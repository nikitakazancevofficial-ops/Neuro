package com.kazancev.ai_chat_companion.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kazancev.ai_chat_companion.AI_MODE_INSTANT
import com.kazancev.ai_chat_companion.AI_MODE_THINKING
import com.kazancev.ai_chat_companion.AiSettings
import com.kazancev.ai_chat_companion.Author
import com.kazancev.ai_chat_companion.ChatInfo
import com.kazancev.ai_chat_companion.ChatMessage
import com.kazancev.ai_chat_companion.ChatSearchResult
import com.kazancev.ai_chat_companion.ChatViewModel
import com.kazancev.ai_chat_companion.ImageGenerationInfo
import com.kazancev.ai_chat_companion.MODEL_GEMMA_INSTANT
import com.kazancev.ai_chat_companion.MODEL_GEMMA_STANDARD
import com.kazancev.ai_chat_companion.MODEL_GLM_STANDARD
import com.kazancev.ai_chat_companion.MODEL_NEMOTRON_EXTENDED
import com.kazancev.ai_chat_companion.MODEL_QWEN_STANDARD
import com.kazancev.ai_chat_companion.MODEL_OMNICODER_9B
import com.kazancev.ai_chat_companion.STANDARD_THINKING_MODELS
import com.kazancev.ai_chat_companion.THINKING_EFFORT_EXTENDED
import com.kazancev.ai_chat_companion.THINKING_EFFORT_STANDARD
import com.kazancev.ai_chat_companion.ui.theme.LocalNeuroPalette
import com.kazancev.ai_chat_companion.ui.i18n.tr
import com.kazancev.ai_chat_companion.ui.i18n.LocalAppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.loadAppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.translate
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import java.util.Locale

object AppColors {
    val background: Color @Composable get() = LocalNeuroPalette.current.background
    val surface: Color @Composable get() = LocalNeuroPalette.current.background
    val elevated: Color @Composable get() = LocalNeuroPalette.current.elevated
    val softSurface: Color @Composable get() = LocalNeuroPalette.current.softSurface
    val drawerBackground: Color @Composable get() = LocalNeuroPalette.current.drawerBackground
    val codeSurface: Color @Composable get() = LocalNeuroPalette.current.codeSurface
    val text: Color @Composable get() = LocalNeuroPalette.current.text
    val subtleText: Color @Composable get() = LocalNeuroPalette.current.subtleText
    val mutedText: Color @Composable get() = LocalNeuroPalette.current.mutedText
    val actionIcon: Color @Composable get() = LocalNeuroPalette.current.actionIcon
    val accent: Color @Composable get() = LocalNeuroPalette.current.accent
    val accentDark: Color @Composable get() = LocalNeuroPalette.current.accentDark
    val blue: Color @Composable get() = LocalNeuroPalette.current.blue
    val userBubble: Color @Composable get() = LocalNeuroPalette.current.userBubble
    val aiText: Color @Composable get() = LocalNeuroPalette.current.text
    val userText: Color @Composable get() = LocalNeuroPalette.current.userText
    val inputBackground: Color @Composable get() = LocalNeuroPalette.current.inputBackground
    val thinkingBubble: Color @Composable get() = LocalNeuroPalette.current.thinkingBubble
    val divider: Color @Composable get() = LocalNeuroPalette.current.divider
    val danger: Color @Composable get() = LocalNeuroPalette.current.danger
}

private enum class VoiceInputState {
    Idle,
    Recording,
    Transcribing
}

private data class FullscreenImage(
    val uri: String,
    val generated: Boolean
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: Int? = null,
    onOpenChat: (Int) -> Unit = {},
    onNewChat: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val aiSettings by viewModel.aiSettings.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var userInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenImage by remember { mutableStateOf<FullscreenImage?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showIntelligenceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var reopenDrawerAfterSettings by remember { mutableStateOf(false) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var drawerSearchMode by remember { mutableStateOf(false) }
    var drawerSearchQuery by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf(VoiceInputState.Idle) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    val amplitudeValues = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    var amplitudeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val streamKey = messages.lastOrNull()?.let {
        "${it.text.length}:${it.reasoning.length}:${it.imageUri}:${it.isStreaming}:${it.isThinking}"
    }
    val hasMessages by remember {
        derivedStateOf { messages.isNotEmpty() }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {}
    )

    fun sendCurrentMessage(input: String = userInput, image: Uri? = selectedImageUri) {
        if (input.isBlank() && image == null) return
        requestLocationPermissionIfNeeded(context, locationPermissionLauncher::launch)
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        autoScrollEnabled = true
        viewModel.sendMessage(input, imageUri = image?.toString())
        userInput = ""
        selectedImageUri = null
    }

    fun releaseVoiceRecorder() {
        voiceRecorder?.let { recorder -> runCatching { recorder.release() } }
        voiceRecorder = null
    }

    fun startVoiceRecording() {
        if (voiceState != VoiceInputState.Idle || isGenerating) return
        releaseVoiceRecorder()
        amplitudeJob?.cancel()
        for (i in amplitudeValues.indices) amplitudeValues[i] = 0f
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceFile = file
            voiceRecorder = recorder
            voiceState = VoiceInputState.Recording
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            amplitudeJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val amp = recorder.getMaxAmplitude().coerceAtLeast(1)
                    val normalized = (kotlin.math.log10(amp.toDouble()) / 4.0).coerceIn(0.0, 1.0).toFloat()
                    amplitudeValues.add(normalized)
                    if (amplitudeValues.size > 36) amplitudeValues.removeAt(0)
                    delay(40)
                }
            }
        } catch (error: Exception) {
            amplitudeJob?.cancel()
            amplitudeJob = null
            runCatching { recorder.release() }
            file.delete()
            context.toast("Не удалось начать запись: ${error.message ?: "проверьте микрофон"}")
            voiceState = VoiceInputState.Idle
            voiceFile = null
        }
    }

    fun stopVoiceRecording(): File? {
        val file = voiceFile
        val recorder = voiceRecorder ?: return file
        return try {
            recorder.stop()
            file
        } catch (error: RuntimeException) {
            file?.delete()
            context.toast("Запись слишком короткая")
            null
        } finally {
            runCatching { recorder.release() }
            voiceRecorder = null
        }
    }

    fun cancelVoiceInput() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        releaseVoiceRecorder()
        voiceFile?.delete()
        voiceFile = null
        voiceState = VoiceInputState.Idle
        for (i in amplitudeValues.indices) amplitudeValues[i] = 0f
    }

    fun appendTranscript(current: String, transcript: String): String {
        val clean = transcript.trim()
        if (clean.isBlank()) return current
        return if (current.isBlank()) clean else "${current.trimEnd()} $clean"
    }

    fun finishVoiceInput(sendAfterTranscription: Boolean) {
        if (voiceState == VoiceInputState.Transcribing) return
        amplitudeJob?.cancel()
        amplitudeJob = null
        val file = stopVoiceRecording()
        if (file == null) {
            voiceFile = null
            voiceState = VoiceInputState.Idle
            return
        }

        voiceState = VoiceInputState.Transcribing
        scope.launch {
            viewModel.transcribeAudio(file).onSuccess { transcript ->
                val updatedInput = appendTranscript(userInput, transcript)
                userInput = updatedInput
                if (sendAfterTranscription) {
                    sendCurrentMessage(updatedInput, selectedImageUri)
                }
            }.onFailure { error ->
                context.toast("Не удалось расшифровать голос: ${error.message ?: "сервер Whisper недоступен"}")
            }
            file.delete()
            if (voiceFile == file) voiceFile = null
            voiceState = VoiceInputState.Idle
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                startVoiceRecording()
            } else {
                context.toast("Разрешите микрофон, чтобы диктовать текст")
            }
        }
    )
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) selectedImageUri = uri }
    )

    DisposableEffect(Unit) {
        onDispose {
            amplitudeJob?.cancel()
            amplitudeJob = null
            cancelVoiceInput()
        }
    }

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
        viewModel.loadChats()
    }

    LaunchedEffect(drawerSearchQuery) {
        viewModel.searchChats(drawerSearchQuery)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layout.totalItemsCount
            total == 0 || lastVisible >= total - 2
        }.collect { atBottom ->
            autoScrollEnabled = atBottom
        }
    }

    LaunchedEffect(messages.size, streamKey) {
        if (messages.isNotEmpty()) {
            withFrameNanos { }
            val bottomAnchorIndex = messages.size
            if (isGenerating) {
                listState.scrollToItem(bottomAnchorIndex)
            } else if (autoScrollEnabled) {
                listState.scrollToItem(bottomAnchorIndex)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                chats = chats,
                searchResults = searchResults,
                searchMode = drawerSearchMode,
                searchQuery = drawerSearchQuery,
                onSearchQueryChange = { drawerSearchQuery = it },
                onOpenSearch = { drawerSearchMode = true },
                onCloseSearch = {
                    drawerSearchMode = false
                    drawerSearchQuery = ""
                    viewModel.clearSearch()
                },
                onOpenChat = { id ->
                    scope.launch { drawerState.close() }
                    drawerSearchMode = false
                    drawerSearchQuery = ""
                    viewModel.clearSearch()
                    onOpenChat(id)
                },
                onNewChat = {
                    scope.launch { drawerState.close() }
                    onNewChat()
                },
                onOpenSettings = {
                    reopenDrawerAfterSettings = true
                    scope.launch { drawerState.close() }
                    showSettingsSheet = true
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = AppColors.background,
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    ChatTopBar(
                        aiSettings = aiSettings,
                        onOpenDrawer = {
                            viewModel.loadChats()
                            scope.launch { drawerState.open() }
                        },
                        onNewChat = onNewChat,
                        onModeSelected = { viewModel.setAiMode(it) },
                        onOpenIntelligence = { showIntelligenceSheet = true }
                    )
                },
                bottomBar = {
                    ChatComposer(
                        value = userInput,
                        selectedImageUri = selectedImageUri,
                        isGenerating = isGenerating,
                        voiceState = voiceState,
                        amplitudeValues = amplitudeValues,
                        onValueChange = { userInput = it },
                        onOpenAttachments = { showAttachmentSheet = true },
                        onRemoveImage = { selectedImageUri = null },
                        onSend = { sendCurrentMessage() },
                        onStop = { viewModel.stopGeneration() },
                        onStartVoice = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                startVoiceRecording()
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onCancelVoice = { cancelVoiceInput() },
                        onStopVoice = { finishVoiceInput(sendAfterTranscription = false) },
                        onSendVoice = { finishVoiceInput(sendAfterTranscription = true) }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.background)
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(messages) { message ->
                        MessageRow(
                            message = message,
                            shareUrl = currentChatId?.let { viewModel.shareCurrentChatUrl() },
                            onRegenerate = { viewModel.regenerateLastResponse() },
                            onOpenImage = { uri, generated ->
                                fullscreenImage = FullscreenImage(uri, generated)
                            }
                        )
                    }
                    item(key = "bottom-anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            if (showAttachmentSheet) {
                AttachmentSheet(
                    hasMessages = hasMessages,
                    onDismiss = { showAttachmentSheet = false },
                    onPickPhoto = {
                        showAttachmentSheet = false
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            if (showIntelligenceSheet) {
                IntelligenceSheet(
                    settings = aiSettings,
                    onDismiss = { showIntelligenceSheet = false },
                    onModeSelected = { viewModel.setAiMode(it) },
                    onThinkingEffortSelected = { viewModel.setThinkingEffort(it) },
                    onStandardModelSelected = { viewModel.setStandardModel(it) },
                    onAutoSwitchChanged = { viewModel.setAutoSwitchToThinking(it) }
                )
            }

            if (showSettingsSheet) {
                SettingsSheet(
                    onDismiss = {
                        showSettingsSheet = false
                        if (reopenDrawerAfterSettings) {
                            reopenDrawerAfterSettings = false
                            scope.launch { drawerState.open() }
                        }
                    }
                )
            }
        }
    }

    fullscreenImage?.let { image ->
        FullscreenImageViewer(
            image = image,
            onDismiss = { fullscreenImage = null }
        )
    }
}

@Composable
private fun ChatTopBar(
    aiSettings: AiSettings,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,  // ← Передаём callback
    onModeSelected: (String) -> Unit,
    onOpenIntelligence: () -> Unit
) {
    val context = LocalContext.current
    var thinkingMenuOpen by remember { mutableStateOf(false) }
    var chatMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(size = 49.dp, onClick = onOpenDrawer) {
            Icon(NeuroIcons.Menu, contentDescription = "Меню", tint = AppColors.text, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Box {
            Surface(
                onClick = { thinkingMenuOpen = true },
                color = AppColors.elevated,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 9.dp
            ) {
                Text(
                    text = "Thinking",
                    color = if (aiSettings.mode == AI_MODE_INSTANT) AppColors.subtleText else AppColors.blue,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(horizontal = 19.dp, vertical = 9.dp)
                )
            }
            ThinkingQuickMenu(
                expanded = thinkingMenuOpen,
                settings = aiSettings,
                onDismiss = { thinkingMenuOpen = false },
                onModeSelected = {
                    thinkingMenuOpen = false
                    onModeSelected(it)
                },
                onOpenIntelligence = {
                    thinkingMenuOpen = false
                    onOpenIntelligence()
                }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            color = AppColors.elevated,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 9.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(NeuroIcons.Edit, contentDescription = "Новый чат", tint = AppColors.text, modifier = Modifier.size(25.dp))
                }
                IconButton(
                    onClick = { chatMenuOpen = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(NeuroIcons.More, contentDescription = "Ещё", tint = AppColors.text, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
    ChatOptionsMenu(
        expanded = chatMenuOpen,
        onDismiss = { chatMenuOpen = false },
        onAction = { title ->
            chatMenuOpen = false
            context.toast("$title: функция готовится")
        }
    )
}

@Composable
private fun ChatOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    CleanPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        width = 310.dp,
        alignment = Alignment.TopEnd,
        offsetX = (-18).dp,
        offsetY = 72.dp,
        cornerRadius = 28.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                "Действия с чатом",
                color = AppColors.subtleText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
            )
            ChatOptionsRow(NeuroIcons.Share, "Поделиться") { onAction("Поделиться") }
            ChatOptionsRow(Icons.Outlined.GroupAdd, "Добавить участников") { onAction("Добавить участников") }
            ChatOptionsRow(Icons.Outlined.FolderOpen, "Добавить в проект") { onAction("Добавить в проект") }
            ChatOptionsRow(NeuroIcons.Paperclip, "Загруженные файлы") { onAction("Загруженные файлы") }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.divider))
            Spacer(Modifier.height(5.dp))
            ChatOptionsRow(Icons.Outlined.Archive, "Архивировать") { onAction("Архивировать") }
            ChatOptionsRow(Icons.Outlined.DeleteOutline, "Удалить", danger = true) { onAction("Удалить") }
        }
    }
}

@Composable
private fun ChatOptionsRow(
    icon: ImageVector,
    title: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) AppColors.danger else AppColors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(tr(title), color = color, fontSize = 18.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun RoundIconButton(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = AppColors.elevated,
        shape = CircleShape,
        shadowElevation = 9.dp,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun ThinkingQuickMenu(
    expanded: Boolean,
    settings: AiSettings,
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit,
    onOpenIntelligence: () -> Unit
) {
    CleanPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        width = 258.dp,
        offsetY = 48.dp,
        cornerRadius = 30.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = currentModelLabel(settings),
                color = AppColors.subtleText,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            QuickModeRow(
                title = "Instant",
                selected = settings.mode == AI_MODE_INSTANT,
                onClick = { onModeSelected(AI_MODE_INSTANT) }
            )
            QuickModeRow(
                title = "Thinking",
                selected = settings.mode == AI_MODE_THINKING,
                onClick = { onModeSelected(AI_MODE_THINKING) }
            )
            Spacer(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.divider)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenIntelligence)
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(NeuroIcons.Sliders, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(25.dp))
                Text(
                    "Конфигурация",
                    color = AppColors.text,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickModeRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            if (selected) {
                Icon(NeuroIcons.Check, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(23.dp))
            }
        }
        Text(tr(title), color = AppColors.text, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CleanPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp,
    alignment: Alignment = Alignment.TopStart,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    cornerRadius: Dp,
    content: @Composable () -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    val shape = RoundedCornerShape(cornerRadius)
    Popup(
        alignment = alignment,
        offset = with(density) { IntOffset(offsetX.roundToPx(), offsetY.roundToPx()) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true
        )
    ) {
        Surface(
            color = AppColors.elevated,
            contentColor = AppColors.text,
            shape = shape,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .width(width)
                .border(1.dp, AppColors.divider, shape)
                .clip(shape)
        ) {
            Box(
                modifier = Modifier
                    .background(AppColors.elevated)
            ) {
                content()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun IntelligenceSheet(
    settings: AiSettings,
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit,
    onThinkingEffortSelected: (String) -> Unit,
    onStandardModelSelected: (String) -> Unit,
    onAutoSwitchChanged: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var effortMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.elevated,
        contentColor = AppColors.text,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .size(width = 46.dp, height = 5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(AppColors.mutedText)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Интеллект"), color = AppColors.text, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                IntelligenceRow(
                    title = "Модель",
                    subtitle = currentModelLabel(settings),
                    trailingIcon = if (modelMenuExpanded) NeuroIcons.ChevronUp else NeuroIcons.ChevronDown,
                    onClick = { modelMenuExpanded = true }
                )
                ModelDropdown(
                    expanded = modelMenuExpanded,
                    settings = settings,
                    onDismiss = { modelMenuExpanded = false },
                    onExtendedSelected = {
                        modelMenuExpanded = false
                        onModeSelected(AI_MODE_THINKING)
                        onThinkingEffortSelected(THINKING_EFFORT_EXTENDED)
                    },
                    onInstantSelected = {
                        modelMenuExpanded = false
                        onModeSelected(AI_MODE_INSTANT)
                    },
                    onStandardSelected = {
                        modelMenuExpanded = false
                        onStandardModelSelected(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.softSurface)
            ) {
                IntelligenceChoiceRow(
                    title = "Instant",
                    subtitle = "Для повседневных чатов",
                    selected = settings.mode == AI_MODE_INSTANT,
                    onClick = { onModeSelected(AI_MODE_INSTANT) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.divider)
                )
                IntelligenceChoiceRow(
                    title = "Thinking",
                    subtitle = "Для решения сложных вопросов",
                    selected = settings.mode == AI_MODE_THINKING,
                    onClick = { onModeSelected(AI_MODE_THINKING) }
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            if (settings.mode == AI_MODE_THINKING) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IntelligenceRow(
                        title = "Уровень усилий Thinking",
                        subtitle = if (settings.thinkingEffort == THINKING_EFFORT_EXTENDED) "Расширенное" else "Стандартный",
                        trailingIcon = if (effortMenuExpanded) NeuroIcons.ChevronUp else NeuroIcons.ChevronDown,
                        onClick = { effortMenuExpanded = true }
                    )
                    EffortDropdown(
                        expanded = effortMenuExpanded,
                        settings = settings,
                        onDismiss = { effortMenuExpanded = false },
                        onSelected = {
                            effortMenuExpanded = false
                            onThinkingEffortSelected(it)
                        }
                    )
                }
                Text(
                    text = "Более тщательный подход позволит получить более полные ответы, но это может занять больше времени.",
                    color = AppColors.actionIcon,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            } else {
                AutoSwitchRow(
                    enabled = settings.autoSwitchToThinking,
                    onChanged = onAutoSwitchChanged
                )
                Text(
                    text = "Neuro может автоматически переключаться с Instant на Thinking, когда вы задаёте сложный вопрос.",
                    color = AppColors.actionIcon,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun ModelDropdown(
    expanded: Boolean,
    settings: AiSettings,
    onDismiss: () -> Unit,
    onExtendedSelected: () -> Unit,
    onInstantSelected: () -> Unit,
    onStandardSelected: (String) -> Unit
) {
    CleanPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        width = 292.dp,
        alignment = Alignment.TopEnd,
        offsetY = 58.dp,
        cornerRadius = 26.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DropdownCheckRow(
                title = "Последняя • 5.5",
                subtitle = MODEL_NEMOTRON_EXTENDED,
                selected = settings.mode == AI_MODE_THINKING && settings.thinkingEffort == THINKING_EFFORT_EXTENDED,
                onClick = onExtendedSelected
            )

            STANDARD_THINKING_MODELS.forEach { model ->
                DropdownCheckRow(
                    title = standardModelTitle(model),
                    subtitle = model,
                    selected = settings.mode == AI_MODE_THINKING &&
                            settings.thinkingEffort == THINKING_EFFORT_STANDARD &&
                            settings.standardModel == model,
                    onClick = { onStandardSelected(model) }
                )
            }

            DropdownCheckRow(
                title = "Instant",
                subtitle = MODEL_GEMMA_INSTANT,
                selected = settings.mode == AI_MODE_INSTANT,
                onClick = onInstantSelected
            )
        }
    }
}

@Composable
private fun EffortDropdown(
    expanded: Boolean,
    settings: AiSettings,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    CleanPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        width = 230.dp,
        alignment = Alignment.TopEnd,
        offsetY = -96.dp,
        cornerRadius = 26.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownCheckRow(
                title = "Стандартный",
                selected = settings.thinkingEffort == THINKING_EFFORT_STANDARD,
                onClick = { onSelected(THINKING_EFFORT_STANDARD) }
            )
            DropdownCheckRow(
                title = "Расширенное",
                selected = settings.thinkingEffort == THINKING_EFFORT_EXTENDED,
                onClick = { onSelected(THINKING_EFFORT_EXTENDED) }
            )
        }
    }
}

@Composable
private fun DropdownCheckRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), color = AppColors.text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(tr(subtitle), color = AppColors.subtleText, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(NeuroIcons.Check, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun IntelligenceRow(
    title: String,
    subtitle: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.softSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), color = AppColors.text, fontSize = 18.sp, lineHeight = 23.sp)
            Text(tr(subtitle), color = AppColors.actionIcon, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(trailingIcon, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(27.dp))
    }
}

@Composable
private fun IntelligenceChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), color = AppColors.text, fontSize = 19.sp, lineHeight = 24.sp)
            Text(tr(subtitle), color = AppColors.actionIcon, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Icon(NeuroIcons.Check, contentDescription = null, tint = AppColors.actionIcon, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun AutoSwitchRow(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.softSurface)
            .clickable { onChanged(!enabled) }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Автоматическое\nпереключение на Thinking",
            color = AppColors.text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = { onChanged(!enabled) },
            color = if (enabled) AppColors.text else AppColors.softSurface,
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier.size(width = 70.dp, height = 42.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun currentModelLabel(settings: AiSettings): String {
    return when {
        settings.mode == AI_MODE_INSTANT -> "Instant"
        settings.thinkingEffort == THINKING_EFFORT_EXTENDED -> "Последняя • 5.5"
        else -> standardModelTitle(settings.standardModel)
    }
}

private fun standardModelTitle(model: String): String {
    return when (model) {
        MODEL_QWEN_STANDARD -> "Qwen 3.5 • 9B"
        MODEL_GLM_STANDARD -> "GLM 4.6V Flash"
        MODEL_GEMMA_STANDARD -> "Gemma 4 • E4B"
        MODEL_OMNICODER_9B -> "OmniCoder • 9B"
        else -> model
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    shareUrl: String?,
    onRegenerate: () -> Unit,
    onOpenImage: (String, Boolean) -> Unit
) {
    if (message.author == Author.USER) {
        UserMessage(message, onOpenImage)
    } else {
        AssistantMessage(message, shareUrl, onRegenerate, onOpenImage)
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    onOpenImage: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            message.imageUri?.let { uri ->
                AttachedImagePreview(
                    uri = uri,
                    removable = false,
                    style = ImagePreviewStyle.Message,
                    onClick = { onOpenImage(uri, false) }
                )
            }
            if (message.text.isNotBlank()) {
                Surface(
                    color = AppColors.userBubble,
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.widthIn(max = 310.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = AppColors.userText,
                            fontSize = 20.sp,
                            lineHeight = 27.sp,
                            modifier = Modifier.padding(horizontal = 23.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    shareUrl: String?,
    onRegenerate: () -> Unit,
    onOpenImage: (String, Boolean) -> Unit
) {
    message.imageGeneration?.let { imageGeneration ->
        ImageGenerationMessage(imageGeneration, onOpenImage)
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (message.isThinking && message.text.isBlank()) {
            ThinkingLiveText()
        }

        if (!message.isThinking && message.thinkingStartedAt != null) {
            ThinkingSummary(message)
        }

        if (!message.memoryUpdated.isNullOrBlank()) {
            MemoryUpdatedRow(message.memoryUpdated)
        }

        if (message.text.isNotBlank()) {
            AssistantMarkdown(
                text = message.text
            )
            if (message.isStreaming) {
                StreamingPulse()
            } else {
                MessageActions(text = message.text, shareUrl = shareUrl, onRegenerate = onRegenerate)
            }
        }
    }
}

@Composable
private fun ImageGenerationMessage(
    imageGeneration: ImageGenerationInfo,
    onOpenImage: (String, Boolean) -> Unit
) {
    val isPending =
        imageGeneration.status == "planning" ||
            imageGeneration.status == "generating" ||
            imageGeneration.status == "reviewing"
    val elapsed = imageGeneration.elapsedSeconds?.toInt()
        ?: imageGeneration.startedAt?.let { started ->
            ((imageGeneration.completedAt ?: (System.currentTimeMillis() / 1000)) - started).toInt().coerceAtLeast(1)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            text = when {
                imageGeneration.status == "reviewing" -> tr("Проверяю результат")
                isPending -> tr("Думаю")
                imageGeneration.status == "completed" && elapsed != null -> "${tr("Думал")} $elapsed ${tr("с")}"
                else -> tr("Не удалось создать изображение")
            },
            color = AppColors.subtleText,
            fontSize = 21.sp,
            lineHeight = 27.sp
        )

        when (imageGeneration.status) {
            "completed" -> {
                imageGeneration.reply?.takeIf { it.isNotBlank() }?.let { reply ->
                    AssistantMarkdown(text = reply)
                }
                GeneratedImageCard(
                    imageGeneration = imageGeneration,
                    onOpenImage = { uri -> onOpenImage(uri, true) }
                )
            }
            "failed" -> ImageGenerationErrorCard(imageGeneration.error.orEmpty())
            else -> ImageGenerationProgressCard(imageGeneration)
        }
    }
}

@Composable
private fun ImageGenerationProgressCard(imageGeneration: ImageGenerationInfo) {
    val transition = rememberInfiniteTransition(label = "image_generation_dots")
    val horizontal by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(animation = tween(3100), repeatMode = RepeatMode.Reverse),
        label = "image_generation_horizontal"
    )
    val vertical by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(animation = tween(2400), repeatMode = RepeatMode.Reverse),
        label = "image_generation_vertical"
    )
    val breathing by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(1450), repeatMode = RepeatMode.Reverse),
        label = "image_generation_breathing"
    )
    val aspectRatio = (imageGeneration.width.toFloat() / imageGeneration.height.toFloat()).coerceIn(0.62f, 1.78f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 438.dp)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF222222))
    ) {
        val spacing = 15.dp.toPx()
        val focus = Offset(size.width * horizontal, size.height * vertical)
        val lightRadius = max(size.width, size.height) * 0.56f * breathing
        var y = spacing
        while (y < size.height) {
            var x = spacing
            while (x < size.width) {
                val dx = x - focus.x
                val dy = y - focus.y
                val intensity = (1f - sqrt(dx * dx + dy * dy) / lightRadius).coerceIn(0f, 1f)
                if (intensity > 0.04f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.025f + intensity * intensity * 0.31f),
                        radius = 0.65.dp.toPx() + intensity * 2.15.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                x += spacing
            }
            y += spacing
        }
    }
}

@Composable
private fun GeneratedImageCard(
    imageGeneration: ImageGenerationInfo,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val uri = imageGeneration.url.orEmpty()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                URL(uri).openStream().use { input -> BitmapFactory.decodeStream(input) }
            }.getOrNull()
        }
    }
    val aspectRatio = (imageGeneration.width.toFloat() / imageGeneration.height.toFloat()).coerceIn(0.62f, 1.78f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 438.dp)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(30.dp))
                .background(AppColors.softSurface)
                .clickable(enabled = uri.isNotBlank()) { onOpenImage(uri) }
        ) {
            bitmap?.let { loaded ->
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "Сгенерированное изображение",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(34.dp))
            }

            Surface(
                onClick = { context.toast("Редактор изображения скоро появится здесь") },
                color = Color.Black.copy(alpha = 0.54f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    tr("Редактировать"),
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }

            Surface(
                onClick = { context.shareImage(uri) },
                color = Color.Black.copy(alpha = 0.48f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        NeuroIcons.Share,
                        contentDescription = "Поделиться изображением",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(NeuroIcons.ThumbUp, "Нравится") { context.toast("Спасибо за оценку") }
            ActionIcon(NeuroIcons.ThumbDown, "Не нравится") { context.toast("Учту при следующих генерациях") }
            ActionIcon(NeuroIcons.More, "Ещё") { context.toast("Дополнительные действия скоро появятся здесь") }
        }
    }
}

@Composable
private fun FullscreenImageViewer(
    image: FullscreenImage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = image.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.openImageInputStream(image.uri).use { input -> BitmapFactory.decodeStream(input) }
            }.getOrNull()
        }
    }
    var controlsVisible by remember(image.uri) { mutableStateOf(true) }
    var showMoreMenu by remember(image.uri) { mutableStateOf(false) }
    var scale by remember(image.uri) { mutableStateOf(1f) }
    var translation by remember(image.uri) { mutableStateOf(Offset.Zero) }
    val viewportWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val viewportHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val fittedImageSize = remember(bitmap, viewportWidth, viewportHeight) {
        bitmap?.let { loaded ->
            val imageAspect = loaded.width.toFloat() / loaded.height.toFloat()
            val viewportAspect = viewportWidth / viewportHeight
            if (imageAspect >= viewportAspect) {
                Offset(viewportWidth, viewportWidth / imageAspect)
            } else {
                Offset(viewportHeight * imageAspect, viewportHeight)
            }
        } ?: Offset(viewportWidth, viewportHeight)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(image.uri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = nextScale
                        translation = clampImageTranslation(
                            translation = if (nextScale > 1f) translation + pan else Offset.Zero,
                            scale = nextScale,
                            viewportWidth = viewportWidth,
                            viewportHeight = viewportHeight,
                            fittedImageWidth = fittedImageSize.x,
                            fittedImageHeight = fittedImageSize.y
                        )
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                    if (!controlsVisible) showMoreMenu = false
                }
        ) {
            bitmap?.let { loaded ->
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "Изображение",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = translation.x
                            translationY = translation.y
                        }
                )
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(WindowInsets.statusBars.asPaddingValues())
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FullscreenRoundIconButton(
                            icon = NeuroIcons.Close,
                            contentDescription = "Закрыть",
                            onClick = onDismiss
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FullscreenRoundIconButton(
                                icon = NeuroIcons.Download,
                                contentDescription = "Скачать",
                                onClick = { context.downloadImage(image.uri) }
                            )
                            if (image.generated) {
                                FullscreenRoundIconButton(
                                    icon = NeuroIcons.More,
                                    contentDescription = "Ещё",
                                    onClick = { showMoreMenu = !showMoreMenu }
                                )
                            }
                        }
                    }

                    if (image.generated && showMoreMenu) {
                        FullscreenImageMenu(
                            onGoodAnswer = {
                                showMoreMenu = false
                                context.toast("Спасибо за оценку")
                            },
                            onBadAnswer = {
                                showMoreMenu = false
                                context.toast("Учту при следующих генерациях")
                            },
                            onHelp = {
                                showMoreMenu = false
                                context.toast("Справочный центр")
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(WindowInsets.statusBars.asPaddingValues())
                                .padding(top = 82.dp, end = 20.dp)
                        )
                    }

                    if (image.generated) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(WindowInsets.navigationBars.asPaddingValues())
                                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FullscreenActionButton(
                                label = tr("Редактировать"),
                                onClick = { context.toast("Редактор изображения скоро появится здесь") },
                                modifier = Modifier.weight(1f)
                            )
                            FullscreenActionButton(
                                label = tr("Поделиться"),
                                onClick = { context.shareImage(image.uri) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clampImageTranslation(
    translation: Offset,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    fittedImageWidth: Float,
    fittedImageHeight: Float
): Offset {
    val maxX = max(0f, (fittedImageWidth * scale - viewportWidth) / 2f)
    val maxY = max(0f, (fittedImageHeight * scale - viewportHeight) / 2f)
    return Offset(
        x = translation.x.coerceIn(-maxX, maxX),
        y = translation.y.coerceIn(-maxY, maxY)
    )
}

@Composable
private fun FullscreenRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF191919).copy(alpha = 0.9f),
        contentColor = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFF3C3C3C)),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
private fun FullscreenActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF151515).copy(alpha = 0.92f),
        contentColor = Color.White,
        shape = RoundedCornerShape(36.dp),
        border = BorderStroke(1.dp, Color(0xFF555555)),
        modifier = modifier.height(62.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FullscreenImageMenu(
    onGoodAnswer: () -> Unit,
    onBadAnswer: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF202020),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 12.dp,
        modifier = modifier.width(232.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            FullscreenImageMenuItem(NeuroIcons.ThumbUp, tr("Хороший ответ"), onGoodAnswer)
            FullscreenImageMenuItem(NeuroIcons.ThumbDown, tr("Плохой ответ"), onBadAnswer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .height(1.dp)
                    .background(Color(0xFF454545))
            )
            FullscreenImageMenuItem(NeuroIcons.Help, tr("Справочный центр"), onHelp)
        }
    }
}

@Composable
private fun FullscreenImageMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ImageGenerationErrorCard(error: String) {
    Surface(
        color = AppColors.softSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(tr("Генерация не завершилась"), color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                error.ifBlank { "Проверьте, запущен ли FLUX-воркер, и попробуйте ещё раз." },
                color = AppColors.subtleText,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun MemoryUpdatedRow(memory: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.toast(memory) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            NeuroIcons.Edit,
            contentDescription = null,
            tint = AppColors.subtleText,
            modifier = Modifier.size(23.dp)
        )
        Text(
            "Сохранённая память обновлена",
            color = AppColors.subtleText,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThinkingLiveText() {
    val transition = rememberInfiniteTransition(label = "thinking_text")
    val alpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(animation = tween(820), repeatMode = RepeatMode.Reverse),
        label = "thinking_alpha"
    )

    SelectionContainer {
        Text(
            text = tr("Думаю"),
            color = AppColors.subtleText,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun ThinkingSummary(message: ChatMessage) {
    var expanded by remember(message.reasoning) { mutableStateOf(false) }
    val startedAt = message.thinkingStartedAt ?: return
    val finishedAt = message.thinkingFinishedAt ?: System.currentTimeMillis()
    val seconds = max(1, ((finishedAt - startedAt) / 1000).toInt())
    val label = if (seconds <= 2) {
        "Думал на протяжении пары секунд"
    } else {
        "Думал $seconds сек."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = message.reasoning.isNotBlank()) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SelectionContainer {
            Text(text = label, color = AppColors.subtleText, fontSize = 21.sp, lineHeight = 27.sp)
        }
        if (expanded && message.reasoning.isNotBlank()) {
            Surface(color = AppColors.softSurface, shape = RoundedCornerShape(16.dp)) {
                SelectionContainer {
                    Text(
                        text = message.reasoning,
                        color = Color(0xFF626262),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMarkdown(text: String) {
    val blocks = remember(text) {
        parseMarkdownBlocks(text)
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Code -> CodeBlockCard(block.code)
                    is MarkdownBlock.Heading -> MarkdownHeading(block.text, block.level)
                    is MarkdownBlock.Paragraph -> MarkdownParagraph(block.text)
                    is MarkdownBlock.Quote -> QuoteBlock(block.text)
                    is MarkdownBlock.ListItems -> BulletList(block.items)
                }
            }
        }
    }
}

@Composable
private fun StreamingPulse() {
    val transition = rememberInfiniteTransition(label = "streaming_pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(920), repeatMode = RepeatMode.Restart),
        label = "streaming_phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, start = 1.dp)
    ) {
        repeat(3) { index ->
            val wave = 1f - abs(((phase + index * 0.22f) % 1f) - 0.5f) * 2f
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = (5 + wave * 8).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .alpha(0.28f + wave * 0.72f)
                    .background(AppColors.accent)
            )
        }
    }
}

@Composable
private fun MarkdownHeading(text: String, level: Int) {
    val size = if (level <= 2) 18.sp else 17.sp
    Text(
        text = inlineMarkdown(text, AppColors.codeSurface, AppColors.text),
        color = AppColors.text,
        fontSize = size,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MarkdownParagraph(text: String) {
    Text(
        text = inlineMarkdown(text, AppColors.codeSurface, AppColors.text),
        color = AppColors.text,
        fontSize = 18.sp,
        lineHeight = 25.sp
    )
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("•", color = AppColors.text, fontSize = 18.sp, lineHeight = 25.sp)
                Text(
                    text = inlineMarkdown(item, AppColors.codeSurface, AppColors.text),
                    color = AppColors.text,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuoteBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(AppColors.mutedText)
        )
        Text(
            text = inlineMarkdown(text, AppColors.codeSurface, AppColors.text),
            color = AppColors.text,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeBlockCard(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.codeSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = code.trimEnd(),
            color = AppColors.text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MessageActions(
    text: String,
    shareUrl: String?,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var rating by remember(text) { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
            }
        }
    }

    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIcon(NeuroIcons.Copy, "Копировать") {
                context.copyToClipboard(text)
                context.toast("Ответ скопирован")
            }
            ActionIcon(NeuroIcons.Speaker, "Озвучить") {
                tts.language = Locale.forLanguageTag(language.code)
                tts.speak(text.take(3600), TextToSpeech.QUEUE_FLUSH, null, "assistant-response")
            }
            ActionIcon(NeuroIcons.ThumbUp, "Нравится", active = rating == 1) {
                rating = if (rating == 1) 0 else 1
            }
            ActionIcon(NeuroIcons.ThumbDown, "Не нравится", active = rating == -1) {
                rating = if (rating == -1) 0 else -1
            }
            ActionIcon(NeuroIcons.Share, "Поделиться") {
                context.shareText(shareUrl ?: text)
            }
            ActionIcon(NeuroIcons.More, "Ещё") {
                showMenu = !showMenu
            }
        }
        AnimatedVisibility(visible = showMenu, enter = fadeIn(tween(120)), exit = fadeOut(tween(90))) {
            ResponseMoreMenu(
                onRegenerate = {
                    showMenu = false
                    onRegenerate()
                },
                onBranch = {
                    showMenu = false
                    context.toast("Ветка в новом чате скоро будет здесь")
                },
                onWebSearch = {
                    showMenu = false
                    context.toast("Поиск в сети скоро будет здесь")
                }
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(31.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) AppColors.accent else AppColors.actionIcon,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun ResponseMoreMenu(
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onWebSearch: () -> Unit
) {
    val now = remember { SimpleDateFormat("Сегодня, h:mm a", Locale.getDefault()).format(Date()) }
    Surface(
        color = AppColors.elevated,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.widthIn(min = 278.dp, max = 340.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(now, color = AppColors.subtleText, fontSize = 18.sp, lineHeight = 22.sp)
            MoreMenuItem(NeuroIcons.Branch, "Ветка в новом чате", onBranch)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.divider)
            )
            Text(tr("Используется 5.5 Thinking"), color = AppColors.subtleText, fontSize = 18.sp)
            MoreMenuItem(NeuroIcons.Refresh, "Повторить", onRegenerate)
            MoreMenuItem(NeuroIcons.Globe, "Искать в сети", onWebSearch)
        }
    }
}

@Composable
private fun MoreMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(28.dp))
        Text(tr(title), color = AppColors.text, fontSize = 22.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun ChatComposer(
    value: String,
    selectedImageUri: Uri?,
    isGenerating: Boolean,
    voiceState: VoiceInputState,
    amplitudeValues: List<Float>,
    onValueChange: (String) -> Unit,
    onOpenAttachments: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onStartVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSendVoice: () -> Unit
) {
    val hasImage = selectedImageUri != null
    val isVoiceActive = voiceState != VoiceInputState.Idle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .imePadding()
            .padding(
                start = if (hasImage || isVoiceActive) 18.dp else 30.dp,
                end = if (hasImage || isVoiceActive) 18.dp else 30.dp,
                top = 6.dp,
                bottom = 12.dp
            )
    ) {
        if (isVoiceActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onCancelVoice,
                    color = AppColors.elevated,
                    shape = CircleShape,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.dp, AppColors.divider, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            NeuroIcons.Close,
                            contentDescription = "Отменить запись",
                            tint = AppColors.text,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }

                ComposerPanel(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize()
                ) {
                    VoiceComposerContent(
                        selectedImageUri = selectedImageUri,
                        voiceState = voiceState,
                        amplitudeValues = amplitudeValues,
                        onRemoveImage = onRemoveImage,
                        onStopVoice = onStopVoice,
                        onSendVoice = onSendVoice
                    )
                }
            }
        } else if (hasImage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onOpenAttachments,
                    color = AppColors.elevated,
                    shape = CircleShape,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.dp, AppColors.divider, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(NeuroIcons.Add, contentDescription = "Добавить", tint = AppColors.text, modifier = Modifier.size(29.dp))
                    }
                }

                ComposerPanel(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AttachedImagePreview(
                            uri = selectedImageUri.toString(),
                            removable = true,
                            onRemove = onRemoveImage,
                            style = ImagePreviewStyle.Composer,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ComposerTextField(
                                value = value,
                                onValueChange = onValueChange,
                                placeholder = tr("Спросить Neuro"),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 2.dp, end = 8.dp, bottom = 4.dp)
                            )
                            IconButton(
                                onClick = onStartVoice,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(NeuroIcons.Mic, contentDescription = "Голос", tint = AppColors.subtleText, modifier = Modifier.size(24.dp))
                            }
                            SendStopButton(
                                canSend = true,
                                isGenerating = isGenerating,
                                onSend = onSend,
                                onStop = onStop
                            )
                        }
                    }
                }
            }
        } else {
            ComposerPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenAttachments,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(NeuroIcons.Add, contentDescription = "Добавить", tint = AppColors.text, modifier = Modifier.size(27.dp))
                    }
                    ComposerTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 5.dp, vertical = 8.dp)
                    )
                    IconButton(
                        onClick = onStartVoice,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(NeuroIcons.Mic, contentDescription = "Голос", tint = AppColors.subtleText, modifier = Modifier.size(24.dp))
                    }
                    SendStopButton(
                        canSend = value.isNotBlank(),
                        isGenerating = isGenerating,
                        onSend = onSend,
                        onStop = onStop
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceComposerContent(
    selectedImageUri: Uri?,
    voiceState: VoiceInputState,
    amplitudeValues: List<Float>,
    onRemoveImage: () -> Unit,
    onStopVoice: () -> Unit,
    onSendVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedImageUri != null) {
            AttachedImagePreview(
                uri = selectedImageUri.toString(),
                removable = true,
                onRemove = onRemoveImage,
                style = ImagePreviewStyle.Composer,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (voiceState == VoiceInputState.Transcribing) {
                TranscribingVoiceIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 10.dp)
                )
            } else {
                VoiceWaveform(
                    amplitudeValues = amplitudeValues,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 10.dp)
                )
            }

            VoiceStopButton(
                enabled = voiceState == VoiceInputState.Recording,
                onClick = onStopVoice
            )
            Spacer(modifier = Modifier.width(8.dp))
            VoiceSendButton(
                enabled = voiceState == VoiceInputState.Recording,
                onClick = onSendVoice
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    amplitudeValues: List<Float>,
    modifier: Modifier = Modifier
) {
    val barCount = 36
    val displayValues by remember {
        derivedStateOf {
            amplitudeValues.take(barCount).map { amp ->
                val curved = amp * 0.10f + amp * amp * 0.90f
                curved.coerceIn(0f, 1f)
            }
        }
    }

    Row(
        modifier = modifier.height(36.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (v in displayValues) {
            val baseHeight = 1.dp
            val maxExtra = 31.dp
            val height = baseHeight + (v * maxExtra.value).dp

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.coerceIn(1.dp, 34.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .alpha(0.08f + v * 0.87f)
                    .background(AppColors.accent)
            )
        }
    }
}

@Composable
private fun TranscribingVoiceIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(
            color = AppColors.text,
            strokeWidth = 3.dp,
            modifier = Modifier.size(27.dp)
        )
        Text(
            text = "Расшифровка...",
            color = AppColors.subtleText,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VoiceStopButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        color = AppColors.softSurface,
        shape = CircleShape,
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.58f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.text)
            )
        }
    }
}

@Composable
private fun VoiceSendButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        color = if (enabled) AppColors.text else AppColors.softSurface,
        shape = CircleShape,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                NeuroIcons.ArrowUp,
                contentDescription = "Остановить и отправить",
                tint = if (enabled) Color.White else AppColors.subtleText.copy(alpha = 0.55f),
                modifier = Modifier.size(27.dp)
            )
        }
    }
}

@Composable
private fun ComposerPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    Surface(
        color = AppColors.elevated,
        contentColor = AppColors.text,
        shape = shape,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .shadow(
                elevation = 7.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x12000000),
                spotColor = Color(0x14000000)
            )
            .border(1.dp, AppColors.divider, shape)
    ) {
        content()
    }
}

@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Спросить Neuro"
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 26.dp),
        textStyle = TextStyle(
            color = AppColors.text,
            fontSize = 18.sp,
            lineHeight = 23.sp
        ),
        cursorBrush = SolidColor(AppColors.accent),
        minLines = 1,
        maxLines = 4,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        color = AppColors.subtleText,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun SendStopButton(
    canSend: Boolean,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        onClick = if (isGenerating) onStop else onSend,
        color = AppColors.accent,
        shape = CircleShape,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "send_stop"
            ) { generating ->
                if (generating) {
                    Icon(NeuroIcons.Stop, contentDescription = "Стоп", tint = Color.White, modifier = Modifier.size(26.dp))
                } else {
                    Icon(
                        NeuroIcons.ArrowUp,
                        contentDescription = "Отправить",
                        tint = Color.White.copy(alpha = if (canSend) 1f else 0.55f),
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AttachmentSheet(
    hasMessages: Boolean,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.elevated,
        contentColor = AppColors.text,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(AppColors.mutedText)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 28.dp, end = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Neuro",
                    color = AppColors.text,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = tr("Все фотографии"),
                    color = AppColors.blue,
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.clickable(onClick = onPickPhoto)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onPickPhoto,
                    color = AppColors.softSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(width = 116.dp, height = 116.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(NeuroIcons.Camera, contentDescription = "Выбрать фото", tint = AppColors.text, modifier = Modifier.size(38.dp))
                    }
                }
                repeat(3) { index ->
                    Surface(
                        color = AppColors.softSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(width = 86.dp, height = 116.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (hasMessages) "${index + 1}" else "",
                                color = AppColors.mutedText,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.divider)
            )

            AttachmentAction(NeuroIcons.ImageCreate, "Создать изображение", "Создать любое изображение") {
                context.toast("Создание изображений скоро будет здесь")
            }
            AttachmentAction(NeuroIcons.Telescope, "Глубокое исследование", "Получить подробный отчёт") {
                context.toast("Глубокое исследование скоро будет здесь")
            }
            AttachmentAction(NeuroIcons.Globe, "Поиск в сети", "Искать актуальные новости и информацию") {
                context.toast("Поиск в сети скоро будет здесь")
            }
            AttachmentAction(NeuroIcons.Agent, "Режим агента", "Делает работу за вас") {
                context.toast("Режим агента скоро будет здесь")
            }
            AttachmentAction(NeuroIcons.Paperclip, "Добавить файлы", "Анализ или краткое изложение") {
                onPickPhoto()
            }
        }
    }
}

@Composable
private fun AttachmentAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), color = AppColors.text, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(tr(subtitle), color = AppColors.subtleText, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private enum class ImagePreviewStyle {
    Message,
    Composer
}

@Composable
private fun AttachedImagePreview(
    uri: String,
    removable: Boolean,
    modifier: Modifier = Modifier,
    style: ImagePreviewStyle = ImagePreviewStyle.Message,
    onRemove: () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    URL(uri).openStream().use { input -> BitmapFactory.decodeStream(input) }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            }.getOrNull()
        }
    }

    val loadedBitmap = bitmap
    val aspect = remember(loadedBitmap) {
        if (loadedBitmap != null && loadedBitmap.height > 0) {
            (loadedBitmap.width.toFloat() / loadedBitmap.height.toFloat()).coerceIn(0.62f, 1.9f)
        } else {
            1f
        }
    }
    val maxChatWidth = remember(configuration.screenWidthDp) {
        val available = configuration.screenWidthDp.dp - 48.dp
        if (available < 438.dp) available else 438.dp
    }
    val adaptiveSize = when (style) {
        ImagePreviewStyle.Message -> {
            when {
                aspect >= 1f -> Modifier
                    .width(maxChatWidth)
                    .aspectRatio(aspect)
                aspect >= 0.86f -> Modifier
                    .width(226.dp)
                    .aspectRatio(1f)
                else -> Modifier
                    .width(172.dp)
                    .aspectRatio(aspect)
            }
        }

        ImagePreviewStyle.Composer -> {
            if (aspect >= 1f) {
                Modifier
                    .width(214.dp)
                    .aspectRatio(aspect)
            } else {
                Modifier
                    .size(width = 124.dp, height = 124.dp)
            }
        }
    }

    Box(
        modifier = modifier
            .then(adaptiveSize)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Surface(
            color = AppColors.softSurface,
            shape = RoundedCornerShape(if (style == ImagePreviewStyle.Message) 24.dp else 17.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(NeuroIcons.Image, contentDescription = null, tint = AppColors.actionIcon, modifier = Modifier.size(34.dp))
                }
            }
        }

        if (removable) {
            Surface(
                onClick = onRemove,
                color = AppColors.elevated,
                shape = CircleShape,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(35.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(NeuroIcons.Close, contentDescription = "Убрать фото", tint = AppColors.text, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatDrawer(
    chats: List<ChatInfo>,
    searchResults: List<ChatSearchResult>,
    searchMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenChat: (Int) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    ModalDrawerSheet(
        drawerContainerColor = AppColors.drawerBackground,
        drawerContentColor = AppColors.text,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (searchMode) {
                DrawerSearchContent(
                    query = searchQuery,
                    results = searchResults,
                    onQueryChange = onSearchQueryChange,
                    onClose = onCloseSearch,
                    onOpenChat = onOpenChat
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 36.dp, end = 24.dp),
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                        bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        DrawerHeader(
                            onOpenSearch = onOpenSearch,
                            onOpenSettings = onOpenSettings
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item { DrawerAction(NeuroIcons.Image, "Изображения") { context.toast("Изображения") } }
                    item { DrawerAction(NeuroIcons.AgentCore, "AI Agent") { context.toast("AI Agent") } }
                    item { DrawerAction(NeuroIcons.Library, "Библиотека") { context.toast("Библиотека") } }
                    item { DrawerAction(NeuroIcons.Memory, "Локальная память") { context.toast("Локальная память") } }
                    item { DrawerSectionTitle("Недавнее") }
                    if (chats.isEmpty()) {
                        item {
                            DrawerRecentItem(
                                title = "Приветствие в чате",
                                selected = true,
                                onClick = onNewChat
                            )
                        }
                    } else {
                        items(chats) { chat ->
                            DrawerRecentItem(
                                title = chat.title,
                                selected = false,
                                onClick = { onOpenChat(chat.id) }
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = onNewChat,
                color = AppColors.accent,
                shape = RoundedCornerShape(34.dp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Icon(NeuroIcons.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(tr("Чат"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeuroWordmark(modifier = Modifier.weight(1f))
        Surface(onClick = onOpenSearch, color = AppColors.elevated, shape = RoundedCornerShape(28.dp), shadowElevation = 10.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Icon(NeuroIcons.Search, contentDescription = "Поиск", tint = AppColors.text, modifier = Modifier.size(30.dp))
                Surface(
                    onClick = onOpenSettings,
                    color = AppColors.softSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {}
            }
        }
    }
}

@Composable
private fun DrawerSearchContent(
    query: String,
    results: List<ChatSearchResult>,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenChat: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 22.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp)
    ) {
        Surface(
            color = AppColors.elevated,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(NeuroIcons.Back, contentDescription = "Назад", tint = AppColors.actionIcon, modifier = Modifier.size(25.dp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.text, fontSize = 20.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(AppColors.blue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isBlank()) {
                                Text(tr("Поиск"), color = AppColors.subtleText, fontSize = 20.sp)
                            }
                            inner()
                        }
                    }
                )
                IconButton(onClick = { if (query.isBlank()) onClose() else onQueryChange("") }, modifier = Modifier.size(34.dp)) {
                    Icon(NeuroIcons.Close, contentDescription = "Очистить", tint = AppColors.actionIcon, modifier = Modifier.size(25.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            items(results) { result ->
                SearchResultItem(result = result, onClick = { onOpenChat(result.id) })
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: ChatSearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp)
    ) {
        Text(
            text = result.title,
            color = AppColors.text,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (result.snippet.isNotBlank()) {
            Text(
                text = result.snippet,
                color = AppColors.subtleText,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(21.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(25.dp))
        Text(tr(title), color = AppColors.text, fontSize = 22.sp, lineHeight = 28.sp)
    }
}

@Composable
private fun DrawerInvestmentAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(21.dp)
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6F8ED)),
            contentAlignment = Alignment.Center
        ) {
            Text("$", color = AppColors.accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Text(tr("Инвестиции"), color = AppColors.text, fontSize = 22.sp, lineHeight = 28.sp)
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = tr(title),
        color = AppColors.text,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp, bottom = 5.dp)
    )
}

@Composable
private fun DrawerRecentItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) AppColors.softSurface else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = tr(title),
            color = AppColors.text,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

private sealed class MarkdownBlock {
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Code(val code: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class ListItems(val items: List<String>) : MarkdownBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.replace("\r\n", "\n").split('\n')
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        val text = paragraph.joinToString("\n").trim()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                index += 1
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code += lines[index]
                    index += 1
                }
                if (index < lines.size) index += 1
                blocks += MarkdownBlock.Code(code.joinToString("\n"))
            }

            trimmed.isBlank() -> {
                flushParagraph()
                index += 1
            }

            trimmed.startsWith("#") && trimmed.dropWhile { it == '#' }.startsWith(" ") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks += MarkdownBlock.Heading(trimmed.drop(level).trim(), level)
                index += 1
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote += lines[index].trim().removePrefix(">").trim()
                    index += 1
                }
                blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = lines[index].trim()
                    if (!item.startsWith("- ") && !item.startsWith("* ")) break
                    items += item.drop(2).trim()
                    index += 1
                }
                blocks += MarkdownBlock.ListItems(items)
            }

            else -> {
                paragraph += line
                index += 1
            }
        }
    }

    flushParagraph()
    return blocks
}

private fun inlineMarkdown(raw: String, codeBackground: Color, codeText: Color) = buildAnnotatedString {
    var index = 0
    while (index < raw.length) {
        when {
            raw.startsWith("**", index) -> {
                val end = raw.indexOf("**", startIndex = index + 2)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }

            raw[index] == '`' -> {
                val end = raw.indexOf('`', startIndex = index + 1)
                if (end > index) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeText
                        )
                    ) {
                        append(raw.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(raw[index])
                    index += 1
                }
            }

            raw[index] == '*' && !raw.startsWith("**", index) -> {
                val end = raw.indexOf('*', startIndex = index + 1)
                if (end > index + 1 && !raw.startsWith("*", end + 1)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(raw.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(raw[index])
                    index += 1
                }
            }

            raw[index] == '_' -> {
                val end = raw.indexOf('_', startIndex = index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(raw.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(raw[index])
                    index += 1
                }
            }

            else -> {
                append(raw[index])
                index += 1
            }
        }
    }
}

private fun Context.toast(message: String) {
    Toast.makeText(this, loadAppLanguage(this).translate(message), Toast.LENGTH_SHORT).show()
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Neuro response", text))
}

private fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "Поделиться ответом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private data class ImageFileFormat(
    val extension: String,
    val mimeType: String
)

private fun Context.downloadImage(source: String) {
    if (
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    ) {
        (this as? Activity)?.let { activity ->
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                401
            )
        }
        toast("Разрешите доступ к файлам и нажмите скачать ещё раз")
        return
    }

    val appContext = applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        val result = runCatching { appContext.saveImageToGallery(source) }
        withContext(Dispatchers.Main) {
            appContext.toast(
                if (result.isSuccess) "Изображение сохранено в галерею"
                else "Не удалось сохранить изображение"
            )
        }
    }
}

private fun Context.saveImageToGallery(source: String) {
    val format = resolveImageFileFormat(source)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Neuro_${System.currentTimeMillis()}.${format.extension}")
        put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Neuro")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = contentResolver
    val destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore did not create an image")
    try {
        openImageInputStream(source).use { input ->
            resolver.openOutputStream(destination)?.use { output ->
                input.copyTo(output)
            } ?: error("MediaStore output stream is unavailable")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        }
    } catch (error: Exception) {
        resolver.delete(destination, null, null)
        throw error
    }
}

private fun Context.shareImage(source: String) {
    val appContext = applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        val sharedUri = runCatching { appContext.prepareSharedImage(source) }.getOrNull()
        withContext(Dispatchers.Main) {
            if (sharedUri == null) {
                appContext.toast("Не удалось подготовить изображение")
                return@withContext
            }
            val format = appContext.resolveImageFileFormat(source)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, sharedUri)
                clipData = ClipData.newRawUri("Neuro image", sharedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(
                Intent.createChooser(intent, "Поделиться изображением")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun Context.prepareSharedImage(source: String): Uri {
    val format = resolveImageFileFormat(source)
    val directory = File(cacheDir, "shared-images").apply { mkdirs() }
    val file = File(directory, "Neuro_${System.currentTimeMillis()}.${format.extension}")
    openImageInputStream(source).use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}

private fun Context.resolveImageFileFormat(source: String): ImageFileFormat {
    val mimeType = if (source.startsWith("http://") || source.startsWith("https://")) {
        URLConnection.guessContentTypeFromName(source.substringBefore('?'))
    } else {
        contentResolver.getType(Uri.parse(source))
    }
    return when (mimeType) {
        "image/png" -> ImageFileFormat("png", "image/png")
        "image/webp" -> ImageFileFormat("webp", "image/webp")
        else -> ImageFileFormat("jpg", "image/jpeg")
    }
}

private fun Context.openImageInputStream(source: String): InputStream {
    return if (source.startsWith("http://") || source.startsWith("https://")) {
        URL(source).openStream()
    } else {
        contentResolver.openInputStream(Uri.parse(source))
            ?: error("Image input stream is unavailable")
    }
}

private fun requestLocationPermissionIfNeeded(
    context: Context,
    launch: (Array<String>) -> Unit
) {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!fineGranted && !coarseGranted) {
        launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}
