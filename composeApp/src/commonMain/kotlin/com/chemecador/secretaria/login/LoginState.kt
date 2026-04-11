package com.chemecador.secretaria.login

data class LoginState(
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    val isLoggedIn: Boolean = false,
)
