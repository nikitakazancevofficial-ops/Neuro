package com.kazancev.ai_chat_companion

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kazancev.ai_chat_companion.ui.screens.ChatListScreen
import com.kazancev.ai_chat_companion.ui.screens.ChatScreen
import com.kazancev.ai_chat_companion.ui.screens.LoginScreen
import com.kazancev.ai_chat_companion.ui.screens.MusicPlayerRoute
import com.kazancev.ai_chat_companion.ui.screens.MusicScreen
import com.kazancev.ai_chat_companion.ui.screens.RegisterScreen

@Composable
fun AppNavigation(
    application: Application,
    initialAuthenticated: Boolean? = null
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(application, initialAuthenticated)
    )
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(application))
    val musicViewModel: MusicViewModel = viewModel(factory = MusicViewModelFactory(application))
    val authState by authViewModel.authState.collectAsState()

    if (!authState.isInitialized) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated && navController.currentDestination?.route == "login") {
            navController.navigateToChat("new", clearBackStack = true)
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.isAuthenticated) "chat/new" else "login"
    ) {
        composable("login") {
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }

        composable("register") {
            RegisterScreen(navController = navController, authViewModel = authViewModel)
        }

        composable("chat_list") {
            ChatListScreen(navController = navController, chatViewModel = chatViewModel)
        }

        composable("chat/{chatId}") { backStackEntry ->
            val chatIdString = backStackEntry.arguments?.getString("chatId") ?: "new"
            val chatId = chatIdString.takeUnless { it == "new" }?.toIntOrNull()

            ChatScreen(
                viewModel = chatViewModel,
                chatId = chatId,
                onOpenChat = { selectedChatId ->
                    navController.navigateToChat(selectedChatId.toString())
                },
                onNewChat = {
                    if (chatViewModel.startNewLocalChat()) {
                        navController.navigateToChat("new")
                    }
                },
                onOpenMusic = {
                    navController.navigate("music") { launchSingleTop = true }
                }
            )
        }

        composable("music") {
            MusicScreen(
                viewModel = musicViewModel,
                onBack = { navController.popBackStack() },
                onOpenTrack = { trackId -> navController.navigate("music/player/$trackId") }
            )
        }

        composable("music/player/{trackId}") { backStackEntry ->
            MusicPlayerRoute(
                viewModel = musicViewModel,
                trackId = backStackEntry.arguments?.getString("trackId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.navigateToChat(chatId: String, clearBackStack: Boolean = false) {
    navigate("chat/$chatId") {
        launchSingleTop = true
        if (clearBackStack) {
            popUpTo(graph.id) { inclusive = true }
        }
    }
}
