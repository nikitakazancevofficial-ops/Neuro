package com.kazancev.ai_chat_companion

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val apiService = ApiService(application)
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}