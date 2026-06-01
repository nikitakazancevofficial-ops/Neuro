package com.kazancev.ai_chat_companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kazancev.ai_chat_companion.ChatEvent
import com.kazancev.ai_chat_companion.ChatInfo
import com.kazancev.ai_chat_companion.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import com.kazancev.ai_chat_companion.ui.i18n.tr

@Composable
fun ChatListScreen(
    navController: NavController,
    chatViewModel: ChatViewModel
) {
    val chats by chatViewModel.chats.collectAsState()
    LaunchedEffect(Unit) {
        chatViewModel.loadChats()
        chatViewModel.events.collectLatest { event ->
            if (event is ChatEvent.RefreshChatList) {
                chatViewModel.loadChats()
            }
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    chatViewModel.startNewLocalChat()
                    navController.navigate("chat/new")
                },
                shape = CircleShape,
                containerColor = AppColors.accent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Icon(NeuroIcons.Add, contentDescription = "Новый чат", modifier = Modifier.size(30.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(paddingValues)
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuroWordmark(fontSize = 32.sp)
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    onClick = { navController.navigate("chat/new") },
                    color = AppColors.elevated,
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(NeuroIcons.Edit, contentDescription = "Новый чат", tint = AppColors.text, modifier = Modifier.size(26.dp))
                    }
                }
            }

            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Нажмите +, чтобы начать новый чат"),
                        color = AppColors.subtleText,
                        fontSize = 18.sp,
                        lineHeight = 23.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chats) { chat ->
                        ChatListItem(chat = chat) {
                            navController.navigate("chat/${chat.id}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatInfo, onClick: () -> Unit) {
    Surface(
        color = AppColors.elevated,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x0F000000),
                spotColor = Color(0x14000000)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    color = AppColors.text,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tr("Чат"),
                    color = AppColors.subtleText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
            Icon(NeuroIcons.ChevronDown, contentDescription = null, tint = AppColors.subtleText, modifier = Modifier.size(22.dp))
        }
    }
}
