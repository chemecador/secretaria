package com.chemecador.secretaria.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.login(email, password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
        }
    }

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.signup(email, password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loginWithGoogle()
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loginAsGuest()
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
        }
    }
}
