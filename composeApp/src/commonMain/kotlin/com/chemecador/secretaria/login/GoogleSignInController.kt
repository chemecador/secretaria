package com.chemecador.secretaria.login

import androidx.compose.runtime.Composable

interface GoogleSignInController {
    suspend fun getIdToken(): Result<String>
    suspend fun clearCredentialState()
}

@Composable
expect fun rememberGoogleSignInController(serverClientId: String? = null): GoogleSignInController?
