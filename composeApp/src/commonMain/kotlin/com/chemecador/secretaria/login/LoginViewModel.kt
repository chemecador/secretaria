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
            _state.update { it.copy(isLoading = true, error = null) }
            repository.login(email, password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.toAuthError())
                    }
                }
        }
    }

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.signup(email, password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.toAuthError())
                    }
                }
        }
    }

    fun loginWithGoogle(
        tokenProvider: (suspend () -> Result<String>)? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = tokenProvider
                ?.invoke()
                ?.fold(
                    onSuccess = { idToken -> repository.loginWithGoogle(idToken) },
                    onFailure = { throwable -> Result.failure(throwable) },
                )
                ?: repository.loginWithGoogle()
            result
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.toAuthError(),
                        )
                    }
                }
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.loginAsGuest()
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = throwable.toAuthError())
                    }
                }
        }
    }

    fun resetState() {
        _state.value = LoginState()
    }

    private fun Throwable.toAuthError(): AuthError =
        (this as? AuthException)?.error ?: AuthError.UNKNOWN
}
