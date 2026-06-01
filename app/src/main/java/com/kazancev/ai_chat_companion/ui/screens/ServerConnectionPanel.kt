package com.kazancev.ai_chat_companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazancev.ai_chat_companion.ApiService
import com.kazancev.ai_chat_companion.ui.i18n.tr
import kotlinx.coroutines.launch

@Composable
fun ServerConnectionShortcut(expandOnError: Boolean = false) {
    var expanded by remember { mutableStateOf(expandOnError) }

    LaunchedEffect(expandOnError) {
        if (expandOnError) expanded = true
    }

    TextButton(onClick = { expanded = !expanded }) {
        Text(tr(if (expanded) "Скрыть настройку подключения" else "Настроить подключение к ПК"), color = AppColors.blue)
    }
    if (expanded) {
        ServerConnectionPanel()
    }
}

@Composable
fun ServerConnectionPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val api = remember { ApiService(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = api.getConfiguredServerUrl()
    }

    Surface(
        color = AppColors.elevated,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tr("Подключение к ПК"), color = AppColors.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Скопируйте адрес из консоли Neuro Server и вставьте его сюда."),
                color = AppColors.subtleText,
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    status = null
                },
                label = { Text(tr("Адрес сервера")) },
                placeholder = { Text("http://192.168.1.10:3510") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    isChecking = true
                    status = null
                    scope.launch {
                        api.checkAndSaveServerUrl(serverUrl)
                            .onSuccess {
                                serverUrl = it
                                isSuccess = true
                                status = "Всё настроено. Можно пользоваться Neuro."
                            }
                            .onFailure {
                                isSuccess = false
                                status = "Не удалось подключиться. Проверьте адрес и запущен ли сервер."
                            }
                        isChecking = false
                    }
                },
                enabled = serverUrl.isNotBlank() && !isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    tr(if (isChecking) "Проверяем..." else "Проверить подключение"),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            status?.let {
                Text(
                    tr(it),
                    color = if (isSuccess) AppColors.accent else AppColors.danger,
                    fontSize = 14.sp
                )
            }
        }
    }
}
