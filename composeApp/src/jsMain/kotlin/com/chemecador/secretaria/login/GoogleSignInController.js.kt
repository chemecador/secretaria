package com.chemecador.secretaria.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

private const val WEB_GOOGLE_CLIENT_ID_ATTRIBUTE = "data-secretaria-google-web-client-id"

@Composable
actual fun rememberGoogleSignInController(serverClientId: String?): GoogleSignInController? {
    val resolvedClientId = remember(serverClientId) {
        serverClientId
            ?.takeUnless { it.isBlank() }
            ?: resolveWebGoogleClientId()
    } ?: return null

    return remember(resolvedClientId) {
        JsGoogleSignInController(clientId = resolvedClientId)
    }
}

internal fun resolveWebGoogleClientId(): String? =
    document.documentElement
        ?.getAttribute(WEB_GOOGLE_CLIENT_ID_ATTRIBUTE)
        ?.takeUnless { it.isBlank() }

private class JsGoogleSignInController(
    private val clientId: String,
) : GoogleSignInController {

    override suspend fun getIdToken(): Result<String> {
        val oauth2 = awaitGoogleOauth2()
            ?: return Result.failure(AuthException(AuthError.NOT_SUPPORTED))

        val result = CompletableDeferred<Result<String>>()
        val config = js("{}")
        config.client_id = clientId
        config.scope = GOOGLE_SCOPES
        config.prompt = GOOGLE_PROMPT
        config.callback = callback@{ response: dynamic ->
            if (result.isCompleted) {
                return@callback
            }

            val error = response?.error as? String
            if (error != null) {
                val authError = if (error == ACCESS_DENIED_ERROR) {
                    AuthError.CANCELLED
                } else {
                    AuthError.UNKNOWN
                }
                result.complete(Result.failure(AuthException(authError)))
                return@callback
            }

            val accessToken = response?.access_token as? String
            if (accessToken.isNullOrBlank()) {
                result.complete(Result.failure(AuthException(AuthError.UNKNOWN)))
            } else {
                result.complete(Result.success(accessToken))
            }
        }
        config.error_callback = errorCallback@{ error: dynamic ->
            if (result.isCompleted) {
                return@errorCallback
            }

            val errorType = error?.type as? String
            val authError = if (errorType == POPUP_CLOSED_ERROR) {
                AuthError.CANCELLED
            } else {
                AuthError.UNKNOWN
            }
            result.complete(Result.failure(AuthException(authError)))
        }

        val tokenClient = oauth2.initTokenClient(config)
        tokenClient.requestAccessToken()
        return result.await()
    }

    override suspend fun clearCredentialState() = Unit
}

private suspend fun awaitGoogleOauth2(): dynamic {
    ensureGoogleIdentityScript()
    repeat(GOOGLE_SCRIPT_RETRY_COUNT) {
        val oauth2 = window.asDynamic().google?.accounts?.oauth2
        if (oauth2 != null) {
            return oauth2
        }
        delay(GOOGLE_SCRIPT_RETRY_DELAY_MS)
    }
    return null
}

private fun ensureGoogleIdentityScript() {
    if (document.getElementById(GOOGLE_IDENTITY_SCRIPT_ID) != null) {
        return
    }

    val script = document.createElement("script")
    script.id = GOOGLE_IDENTITY_SCRIPT_ID
    script.setAttribute("src", GOOGLE_IDENTITY_SCRIPT_URL)
    script.setAttribute("async", "")
    document.head?.appendChild(script)
}

private const val GOOGLE_IDENTITY_SCRIPT_ID = "secretaria-google-identity"
private const val GOOGLE_IDENTITY_SCRIPT_URL = "https://accounts.google.com/gsi/client"
private const val GOOGLE_SCOPES = "openid email profile"
private const val GOOGLE_PROMPT = "select_account"
private const val ACCESS_DENIED_ERROR = "access_denied"
private const val POPUP_CLOSED_ERROR = "popup_closed"
private const val GOOGLE_SCRIPT_RETRY_COUNT = 50
private const val GOOGLE_SCRIPT_RETRY_DELAY_MS = 100L
