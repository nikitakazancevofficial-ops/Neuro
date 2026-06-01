package com.kazancev.ai_chat_companion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kazancev.ai_chat_companion.AuthViewModel
import com.kazancev.ai_chat_companion.ui.i18n.LocalAppLanguage
import com.kazancev.ai_chat_companion.ui.i18n.translate
import com.kazancev.ai_chat_companion.ui.i18n.tr

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val language = LocalAppLanguage.current

    LaunchedEffect(authState.registrationSuccess) {
        if (authState.registrationSuccess) {
            Toast.makeText(context, language.translate("Аккаунт создан"), Toast.LENGTH_SHORT).show()
            navController.popBackStack()
            authViewModel.registrationHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Neuro", color = AppColors.accent, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(tr("Создание аккаунта"), color = AppColors.text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(tr("Один шаг до нового диалога"), color = AppColors.subtleText, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))

            Surface(color = AppColors.elevated, shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = authState.error != null
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr("Пароль")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = authState.error != null
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(tr("Повторите пароль")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassword.isNotEmpty() && password != confirmPassword
                    )
                    authState.error?.let {
                        Text(it, color = AppColors.danger, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { authViewModel.register(email.trim(), password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = email.isNotBlank() && password.isNotBlank() &&
                            password == confirmPassword && !authState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent)
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.height(22.dp))
                        } else {
                            Text(tr("Зарегистрироваться"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            TextButton(onClick = { navController.popBackStack() }) {
                Text(tr("Уже есть аккаунт? Войти"), color = AppColors.blue, fontSize = 16.sp)
            }
        }
    }
}
