package com.kazancev.ai_chat_companion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kazancev.ai_chat_companion.ApiService
import com.kazancev.ai_chat_companion.MemorySettings
import com.kazancev.ai_chat_companion.PersonalizationSettings
import com.kazancev.ai_chat_companion.SavedMemory
import com.kazancev.ai_chat_companion.ui.theme.AppThemeMode
import com.kazancev.ai_chat_companion.ui.theme.LocalAppThemeMode
import com.kazancev.ai_chat_companion.ui.theme.LocalSetAppThemeMode
import com.kazancev.ai_chat_companion.ui.i18n.AppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.LocalAppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.LocalSetAppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.tr
import com.kazancev.ai_chat_companion.ui.i18n.translate
import kotlinx.coroutines.launch

private enum class SettingsPage {
    Main,
    Personalization,
    Memory,
    SavedMemory,
    Language,
    Integrations
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf(SettingsPage.Main) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = AppColors.elevated,
        contentColor = AppColors.text,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxHeight(0.96f)
    ) {
        when (page) {
            SettingsPage.Main -> MainSettingsPage(
                onDismiss = onDismiss,
                onOpen = { page = it }
            )
            SettingsPage.Personalization -> PersonalizationPage(onBack = { page = SettingsPage.Main })
            SettingsPage.Memory -> MemoryPage(
                onBack = { page = SettingsPage.Main },
                onOpenSavedMemory = { page = SettingsPage.SavedMemory }
            )
            SettingsPage.SavedMemory -> SavedMemoryPage(onBack = { page = SettingsPage.Memory })
            SettingsPage.Language -> LanguagePage(onBack = { page = SettingsPage.Main })
            SettingsPage.Integrations -> IntegrationsPage(onBack = { page = SettingsPage.Main })
        }
    }
}

@Composable
private fun MainSettingsPage(
    onDismiss: () -> Unit,
    onOpen: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val showPlaceholder: (String) -> Unit = { title ->
        Toast.makeText(
            context,
            "${language.translate(title)}: ${language.translate("раздел готовится")}",
            Toast.LENGTH_SHORT
        ).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.elevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 78.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            ProfileBlock()

            SettingsSection("Настроить Neuro") {
                SettingsRow(Icons.Outlined.SmartToy, "Персонализация") { onOpen(SettingsPage.Personalization) }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Memory, "Память") { onOpen(SettingsPage.Memory) }
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.Language,
                    "Язык",
                    subtitle = "Язык приложения и ответов Neuro",
                    value = LocalAppLanguage.current.nativeName
                ) { onOpen(SettingsPage.Language) }
                SettingsDivider()
                SettingsRow(
                    Icons.Outlined.Apps,
                    "Локальные интеграции",
                    subtitle = "Подключения для вашего личного пространства"
                ) { onOpen(SettingsPage.Integrations) }
            }

            SettingsSection("Учётная запись") {
                SettingsRow(Icons.Outlined.Email, "Электронная почта", subtitle = "user@neuro.local") {
                    showPlaceholder("Электронная почта")
                }
                SettingsDivider()
                SettingsRow(Icons.Outlined.AddBox, "Подписка", value = "Neuro Plus", showChevron = false) {
                    showPlaceholder("Подписка")
                }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Refresh, "Восстановить покупки", showChevron = false) {
                    showPlaceholder("Восстановить покупки")
                }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Перейти на Neuro Pro",
                    titleColor = AppColors.blue,
                    iconColor = AppColors.blue,
                    showChevron = false
                ) { showPlaceholder("Neuro Pro") }
            }

            SettingsSection("Тема") {
                AppearanceRow()
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.ColorLens,
                    title = "Акцентный цвет",
                    value = "Фиолетовый",
                    valuePrefix = {
                        Box(Modifier.size(14.dp).background(AppColors.accent, CircleShape))
                    },
                    showChevron = false
                ) { showPlaceholder("Акцентный цвет") }
            }

            SettingsSection("Локальный AI") {
                SettingsRow(
                    icon = Icons.Outlined.Memory,
                    title = "Локальное выполнение",
                    subtitle = "LM Studio и генерация работают на вашем ПК",
                    value = "Включено",
                    showChevron = false
                ) { showPlaceholder("Локальное выполнение") }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.Security,
                    title = "Приватный режим",
                    subtitle = "Данные остаются в вашей локальной сети",
                    value = "Включено",
                    showChevron = false
                ) { showPlaceholder("Приватный режим") }
            }

            SettingsSection("Настройки приложения") {
                SettingsRow(Icons.Outlined.Settings, "Общие") { showPlaceholder("Общие") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.NotificationsNone, "Уведомления") { showPlaceholder("Уведомления") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.MicNone, "Голос") { showPlaceholder("Голос") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Lock, "Безопасность") { showPlaceholder("Безопасность") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Policy, "Элементы управления данными") { showPlaceholder("Управление данными") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.FamilyRestroom, "Родительский контроль") { showPlaceholder("Родительский контроль") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Security, "Доверенный контакт") { showPlaceholder("Доверенный контакт") }
            }

            SettingsSection("Помощь") {
                SettingsRow(Icons.Outlined.BugReport, "Сообщить о проблеме\nв приложении") { showPlaceholder("Обратная связь") }
                SettingsDivider()
                SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, "Справочный центр") { showPlaceholder("Справочный центр") }
                SettingsDivider()
                SettingsRow(Icons.Outlined.Info, "Сведения") { showPlaceholder("Сведения") }
            }

            Surface(
                onClick = { showPlaceholder("Выход") },
                color = AppColors.softSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 19.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = AppColors.danger)
                    Text(tr("Выйти"), color = AppColors.danger, fontSize = 19.sp)
                }
            }
        }

        CloseButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp))
    }
}

@Composable
private fun LanguagePage(onBack: () -> Unit) {
    val selectedLanguage = LocalAppLanguage.current
    val setLanguage = LocalSetAppLanguage.current

    SettingsSubPage(title = "Язык", onBack = onBack) {
        HintText("Выбранный язык применяется сразу ко всему интерфейсу и следующим ответам Neuro.")
        SettingsCard {
            AppLanguage.entries.forEachIndexed { index, language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setLanguage(language) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                        if (language == selectedLanguage) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(language.nativeName, color = AppColors.text, fontSize = 18.sp)
                        if (language.nativeName != language.englishName) {
                            Text(language.englishName, color = AppColors.subtleText, fontSize = 14.sp)
                        }
                    }
                }
                if (index != AppLanguage.entries.lastIndex) SettingsDivider()
            }
        }
    }
}

@Composable
private fun PersonalizationPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember { ApiService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(PersonalizationSettings()) }
    var isLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        api.getPersonalization().onSuccess { settings = it }
        isLoaded = true
    }

    SettingsSubPage(
        title = "Персонализация",
        onBack = onBack,
        saveEnabled = isLoaded && !isSaving,
        onSave = {
            isSaving = true
            scope.launch {
                api.updatePersonalization(settings)
                    .onSuccess { Toast.makeText(context, "Персонализация сохранена", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Не удалось сохранить настройки", Toast.LENGTH_SHORT).show() }
                isSaving = false
            }
        }
    ) {
        SelectorCard(
            title = "Базовый стиль и тон",
            value = settings.baseStyle,
            options = listOf("Дружелюбный", "Деловой", "Лаконичный", "Творческий"),
            onSelect = { settings = settings.copy(baseStyle = it) }
        )
        SettingsCard {
            SelectorLine("Доброжелательность", settings.warmth, listOf("Более", "По умолчанию", "Менее")) {
                settings = settings.copy(warmth = it)
            }
            SettingsDivider()
            SelectorLine("Энтузиазм", settings.enthusiasm, listOf("Более", "По умолчанию", "Менее")) {
                settings = settings.copy(enthusiasm = it)
            }
            SettingsDivider()
            SelectorLine("Заголовки и списки", settings.headingsAndLists, listOf("Более", "По умолчанию", "Менее")) {
                settings = settings.copy(headingsAndLists = it)
            }
            SettingsDivider()
            SelectorLine("Эмодзи", settings.emoji, listOf("Более", "По умолчанию", "Менее")) {
                settings = settings.copy(emoji = it)
            }
        }
        SettingsCard {
            ToggleLine("Быстрые ответы", settings.fastAnswers) {
                settings = settings.copy(fastAnswers = it)
            }
        }
        HintText("Иногда Neuro может использовать общие знания, чтобы отвечать быстрее. Персонализация применяется к следующим ответам.")
        SectionLabel("Пользовательские инструкции")
        RoundedInput(
            value = settings.customInstructions,
            placeholder = "Поделитесь чем-нибудь ещё, что вы хотите учитывать...",
            minHeight = 84.dp,
            onValueChange = { settings = settings.copy(customInstructions = it) }
        )
    }
}

@Composable
private fun MemoryPage(
    onBack: () -> Unit,
    onOpenSavedMemory: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { ApiService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(MemorySettings()) }
    var isLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        api.getMemorySettings().onSuccess { settings = it }
        isLoaded = true
    }

    SettingsSubPage(
        title = "Память",
        onBack = onBack,
        saveEnabled = isLoaded && !isSaving,
        onSave = {
            isSaving = true
            scope.launch {
                api.updateMemorySettings(settings)
                    .onSuccess { Toast.makeText(context, "Настройки памяти сохранены", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Не удалось сохранить память", Toast.LENGTH_SHORT).show() }
                isSaving = false
            }
        }
    ) {
        SettingsCard {
            ToggleLine("Ссылаться на историю чата", settings.referenceChatHistory) {
                settings = settings.copy(referenceChatHistory = it)
            }
        }
        HintText("Позволяет Neuro учитывать недавние чаты в ответах.")
        SettingsCard {
            ToggleLine("Ссылаться на сохранённую\nпамять", settings.useSavedMemory) {
                settings = settings.copy(useSavedMemory = it)
            }
        }
        HintText("Позволяет Neuro сохранять важные факты и использовать память при ответе.")
        SettingsCard {
            SettingsRow(Icons.Outlined.Memory, "Сохранённая память", onClick = onOpenSavedMemory)
        }
        HintText("Здесь можно посмотреть и удалить факты, которые Neuro запомнил о вас.")
        SectionLabel("Ваш псевдоним")
        RoundedInput(settings.nickname, "Имя", onValueChange = { settings = settings.copy(nickname = it) })
        SectionLabel("Ваша профессия")
        RoundedInput(settings.profession, "Инженер, студент, пр.", onValueChange = { settings = settings.copy(profession = it) })
        SectionLabel("Больше о вас")
        RoundedInput(
            settings.about,
            "Важные интересы, ценности или предпочтения",
            minHeight = 88.dp,
            onValueChange = { settings = settings.copy(about = it) }
        )
    }
}

@Composable
private fun SavedMemoryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember { ApiService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<SavedMemory>>(emptyList()) }

    fun reload() {
        scope.launch {
            api.getSavedMemories().onSuccess { memories = it }
        }
    }

    LaunchedEffect(Unit) { reload() }

    SettingsSubPage(title = "Сохранённая память", onBack = onBack) {
        if (memories.isEmpty()) {
            SettingsCard {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Пока здесь пусто"), color = AppColors.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Напишите в чате, например: «Запомни, что я люблю программировать».",
                        color = AppColors.subtleText,
                        fontSize = 16.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        } else {
            SettingsCard {
                memories.forEachIndexed { index, memory ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(memory.text, color = AppColors.text, fontSize = 17.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    api.deleteSavedMemory(memory.id)
                                    reload()
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить", tint = AppColors.danger)
                        }
                    }
                    if (index != memories.lastIndex) SettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun IntegrationsPage(onBack: () -> Unit) {
    SettingsSubPage(title = "Локальные интеграции", onBack = onBack) {
        SectionLabel("Подключённые сервисы")
        SettingsCard {
            AppLine("PC", "Local PC", Color(0xFF382B70), Color.White)
            SettingsDivider()
            AppLine("LM", "LM Studio", Color(0xFF171225), Color(0xFF9A82FF))
            SettingsDivider()
            AppLine("FX", "FLUX.2 Klein", Color(0xFFE6DFFF), Color(0xFF4C34C7))
        }
        HintText("Neuro может получать доступ к выбранным вами локальным сервисам. Все подключения остаются под вашим контролем.")
        SettingsCard {
            SettingsRow(Icons.Outlined.Apps, "Добавить интеграцию") {}
        }
    }
}

@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    saveEnabled: Boolean = false,
    onSave: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.elevated)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад", tint = AppColors.text)
            }
            Text(
                tr(title),
                color = AppColors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            if (onSave != null) {
                Surface(
                    onClick = { if (saveEnabled) onSave() },
                    color = AppColors.softSurface,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        tr("Сохранить"),
                        color = if (saveEnabled) AppColors.text else AppColors.mutedText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SelectorCard(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    SettingsCard {
        SelectorLine(title, value, options, onSelect)
    }
}

@Composable
private fun SelectorLine(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr(title),
                color = AppColors.text,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                tr(value),
                color = AppColors.subtleText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp)
            )
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = null,
                tint = AppColors.subtleText,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            SelectorPopover(
                title = title,
                value = value,
                options = options,
                onDismiss = { expanded = false },
                onSelect = {
                    expanded = false
                    onSelect(it)
                }
            )
        }
    }
}

@Composable
private fun SelectorPopover(
    title: String,
    value: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            color = AppColors.elevated,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, AppColors.divider),
            modifier = Modifier.width(310.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                options.forEach { option ->
                    val subtitle = selectorOptionSubtitle(title, option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                            if (option == value) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = AppColors.text,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr(option), color = AppColors.text, fontSize = 19.sp)
                            if (subtitle != null) {
                                Text(
                                    tr(subtitle),
                                    color = AppColors.subtleText,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun selectorOptionSubtitle(title: String, option: String): String? = when (title) {
    "Доброжелательность" -> when (option) {
        "Более" -> "Теплее и внимательнее"
        "Менее" -> "Нейтральнее и сдержаннее"
        else -> null
    }
    "Энтузиазм" -> when (option) {
        "Более" -> "Больше энергии в ответах"
        "Менее" -> "Спокойнее и ровнее"
        else -> null
    }
    "Заголовки и списки" -> when (option) {
        "Более" -> "Чёткое форматирование\nи списки"
        "Менее" -> "Больше абзацев вместо\nсписков"
        else -> null
    }
    "Эмодзи" -> when (option) {
        "Более" -> "Использовать чаще"
        "Менее" -> "Использовать реже"
        else -> null
    }
    else -> null
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tr(title), color = AppColors.text, fontSize = 18.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.accent,
                uncheckedTrackColor = AppColors.softSurface
            )
        )
    }
}

@Composable
private fun RoundedInput(
    value: String,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp = 58.dp,
    onValueChange: (String) -> Unit
) {
    Surface(color = AppColors.softSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = AppColors.text, fontSize = 17.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(AppColors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .padding(horizontal = 18.dp, vertical = 17.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(tr(placeholder), color = AppColors.subtleText, fontSize = 17.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun AppLine(initials: String, title: String, background: Color, foreground: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(34.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Text(initials, color = foreground, fontWeight = FontWeight.Bold)
        }
        Text(title, color = AppColors.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AppColors.subtleText)
    }
}

@Composable
private fun ProfileBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier.size(92.dp).background(AppColors.softSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = AppColors.actionIcon, modifier = Modifier.size(52.dp))
            }
            Surface(color = AppColors.softSurface, shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).size(39.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Изменить профиль", tint = AppColors.text, modifier = Modifier.size(21.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("User", color = AppColors.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppearanceRow() {
    val selectedMode = LocalAppThemeMode.current
    val setMode = LocalSetAppThemeMode.current
    val nextMode = when (selectedMode) {
        AppThemeMode.System -> AppThemeMode.Light
        AppThemeMode.Light -> AppThemeMode.Dark
        AppThemeMode.Dark -> AppThemeMode.System
    }
    val value = when (selectedMode) {
        AppThemeMode.System -> "Системный"
        AppThemeMode.Light -> "Светлый"
        AppThemeMode.Dark -> "Тёмный"
    }
    SettingsRow(Icons.Outlined.DarkMode, "Внешний вид", value = value) { setMode(nextMode) }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        SettingsCard(content)
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        tr(title),
        color = AppColors.subtleText,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        tr(text),
        color = AppColors.subtleText,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(color = AppColors.softSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = AppColors.text,
    iconColor: Color = AppColors.text,
    showChevron: Boolean = true,
    valuePrefix: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 19.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), color = titleColor, fontSize = 18.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(tr(subtitle), color = AppColors.subtleText, fontSize = 15.sp, maxLines = 1)
        }
        if (value != null || valuePrefix != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                valuePrefix?.invoke()
                if (value != null) Text(tr(value), color = AppColors.subtleText, fontSize = 16.sp, maxLines = 1)
            }
        }
        if (showChevron) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AppColors.subtleText)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = AppColors.divider, modifier = Modifier.padding(start = 61.dp, end = 18.dp))
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, color = AppColors.softSurface, shape = CircleShape, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = AppColors.softSurface, shape = CircleShape, modifier = modifier.size(52.dp)) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = AppColors.text, modifier = Modifier.size(30.dp))
        }
    }
}
