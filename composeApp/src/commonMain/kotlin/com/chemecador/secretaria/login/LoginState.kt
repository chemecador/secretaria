package com.chemecador.secretaria.login

data class LoginState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
)
