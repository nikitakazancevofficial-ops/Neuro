package com.kazancev.ai_chat_companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val isInitialized: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val registrationSuccess: Boolean = false
)

class AuthViewModel(
    private val apiService: ApiService,
    initialAuthenticated: Boolean? = null
) : ViewModel() {

    private val _authState = MutableStateFlow(
        AuthState(
            isInitialized = initialAuthenticated != null,
            isAuthenticated = initialAuthenticated ?: false
        )
    )
    val authState = _authState.asStateFlow()

    init {
        if (initialAuthenticated == null) {
            viewModelScope.launch {
                _authState.update {
                    it.copy(
                        isInitialized = true,
                        isAuthenticated = apiService.hasToken()
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            apiService.login(LoginRequest(email, password))
                .onSuccess { response ->
                    if (response.accessToken != null) {
                        apiService.saveToken(response.accessToken)
                        _authState.update { it.copy(isLoading = false, isAuthenticated = true) }
                    } else {
                        _authState.update {
                            it.copy(isLoading = false, error = response.error ?: "Не удалось войти")
                        }
                    }
                }
                .onFailure { error ->
                    _authState.update {
                        it.copy(isLoading = false, error = error.message ?: "Ошибка сети")
                    }
                }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null, registrationSuccess = false) }
            apiService.register(RegisterRequest(email, password))
                .onSuccess { response ->
                    if (response.message != null) {
                        _authState.update { it.copy(isLoading = false, registrationSuccess = true) }
                    } else {
                        _authState.update {
                            it.copy(isLoading = false, error = response.error ?: "Не удалось создать аккаунт")
                        }
                    }
                }
                .onFailure { error ->
                    _authState.update {
                        it.copy(isLoading = false, error = error.message ?: "Ошибка сети")
                    }
                }
        }
    }

    fun registrationHandled() {
        _authState.update { it.copy(registrationSuccess = false, error = null) }
    }
}
