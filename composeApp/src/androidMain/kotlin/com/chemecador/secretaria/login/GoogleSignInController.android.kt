package com.chemecador.secretaria.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

@Composable
actual fun rememberGoogleSignInController(serverClientId: String?): GoogleSignInController? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return null
    val resolvedServerClientId = serverClientId?.takeUnless { it.isBlank() } ?: return null
    val credentialManager = remember(activity) {
        CredentialManager.create(activity)
    }
    val request = remember(resolvedServerClientId) {
        GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(resolvedServerClientId).build())
            .build()
    }

    return remember(activity, credentialManager, request) {
        object : GoogleSignInController {
            override suspend fun getIdToken(): Result<String> {
                return try {
                    val response = credentialManager.getCredential(activity, request)
                    response.toGoogleIdTokenResult()
                } catch (_: GetCredentialCancellationException) {
                    Result.failure(AuthException(AuthError.CANCELLED))
                } catch (_: GetCredentialException) {
                    Result.failure(AuthException(AuthError.UNKNOWN))
                }
            }

            override suspend fun clearCredentialState() {
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            }
        }
    }
}

private fun GetCredentialResponse.toGoogleIdTokenResult(): Result<String> {
    val customCredential = credential as? CustomCredential ?: run {
        return Result.failure(AuthException(AuthError.UNKNOWN))
    }
    if (
        customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL &&
        customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
    ) {
        return Result.failure(AuthException(AuthError.UNKNOWN))
    }

    return try {
        val googleCredential = GoogleIdTokenCredential.createFrom(customCredential.data)
        val idToken = googleCredential.idToken
        if (idToken.isBlank()) {
            Result.failure(AuthException(AuthError.UNKNOWN))
        } else {
            Result.success(idToken)
        }
    } catch (_: GoogleIdTokenParsingException) {
        Result.failure(AuthException(AuthError.UNKNOWN))
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
