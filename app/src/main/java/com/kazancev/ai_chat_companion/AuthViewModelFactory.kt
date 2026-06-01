package com.kazancev.ai_chat_companion

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AuthViewModelFactory(
    private val application: Application,
    private val initialAuthenticated: Boolean? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            val apiService = ApiService(application)
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(apiService, initialAuthenticated) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
