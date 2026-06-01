package com.kazancev.ai_chat_companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kazancev.ai_chat_companion.ui.theme.NeuroThemeHost
import com.kazancev.ai_chat_companion.ui.theme.applySystemBars
import com.kazancev.ai_chat_companion.ui.theme.isDarkTheme
import com.kazancev.ai_chat_companion.ui.theme.loadAppThemeMode
import com.kazancev.ai_chat_companion.ui.i18n.NeuroLanguageHost
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = isDarkTheme(this, loadAppThemeMode(this))
        applySystemBars(window, darkTheme)
        val isAuthenticated = runBlocking { ApiService(application).hasToken() }

        setContent {
            NeuroLanguageHost {
                NeuroThemeHost {
                    AppNavigation(
                        application = application,
                        initialAuthenticated = isAuthenticated
                    )
                }
            }
        }
    }
}
