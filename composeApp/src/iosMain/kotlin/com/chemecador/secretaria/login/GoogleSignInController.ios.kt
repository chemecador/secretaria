@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.chemecador.secretaria.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Composable
actual fun rememberGoogleSignInController(serverClientId: String?): GoogleSignInController? {
    val config = remember {
        IosGoogleSignInConfig(
            clientId = resolveIosGoogleClientId(),
            redirectScheme = resolveIosGoogleReversedClientId(),
        )
    }
    return remember(config) {
        IosGoogleSignInController(config)
    }
}

private class IosGoogleSignInController(
    private val config: IosGoogleSignInConfig,
    private val transport: FirebaseIosAuthTransport = NSURLSessionFirebaseIosAuthTransport(),
) : GoogleSignInController {

    private var activeSession: ASWebAuthenticationSession? = null
    private var contextProvider: AuthenticationPresentationContextProvider? = null

    override suspend fun getIdToken(): Result<String> {
        val codeVerifier = randomUrlSafeToken(64)
        val state = randomUrlSafeToken(24)

        return awaitAuthorizationCode(
            state = state,
            codeVerifier = codeVerifier,
        ).fold(
            onSuccess = { authorizationCode ->
                exchangeAuthorizationCode(
                    authorizationCode = authorizationCode,
                    codeVerifier = codeVerifier,
                )
            },
            onFailure = { throwable -> Result.failure(throwable) },
        )
    }

    override suspend fun clearCredentialState() = Unit

    private suspend fun awaitAuthorizationCode(
        state: String,
        codeVerifier: String,
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val authorizationUrl = NSURL.URLWithString(
            buildAuthorizationUrl(
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                state = state,
                codeVerifier = codeVerifier,
            ),
        )

        if (authorizationUrl == null) {
            continuation.resume(Result.failure(AuthException(AuthError.UNKNOWN)))
            return@suspendCancellableCoroutine
        }

        val presentationContextProvider = AuthenticationPresentationContextProvider()
        val session = ASWebAuthenticationSession(
            authorizationUrl,
            config.redirectScheme,
        ) { callbackUrl: NSURL?, error: NSError? ->
            activeSession = null
            contextProvider = null

            val result = when {
                error != null -> Result.failure(error.toIosGoogleAuthException())
                callbackUrl == null -> Result.failure(AuthException(AuthError.UNKNOWN))
                else -> callbackUrl.toAuthorizationCodeResult(expectedState = state)
            }
            continuation.resumeIfActive(result)
        }

        activeSession = session
        contextProvider = presentationContextProvider
        session.presentationContextProvider = presentationContextProvider
        session.prefersEphemeralWebBrowserSession = false
        continuation.invokeOnCancellation {
            session.cancel()
            activeSession = null
            contextProvider = null
        }

        if (!session.start()) {
            activeSession = null
            contextProvider = null
            continuation.resumeIfActive(Result.failure(AuthException(AuthError.UNKNOWN)))
        }
    }

    private suspend fun exchangeAuthorizationCode(
        authorizationCode: String,
        codeVerifier: String,
    ): Result<String> {
        val response = try {
            transport.post(
                url = GOOGLE_TOKEN_ENDPOINT,
                body = buildIosFormBody(
                    "client_id" to config.clientId,
                    "code" to authorizationCode,
                    "code_verifier" to codeVerifier,
                    "grant_type" to AUTHORIZATION_CODE_GRANT_TYPE,
                    "redirect_uri" to config.redirectUri,
                ),
                contentType = FORM_CONTENT_TYPE,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode !in 200..299) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        val idToken = extractIosJsonString(response.body, "id_token")
            ?.takeUnless { it.isBlank() }
        if (idToken != null) {
            return Result.success(idToken)
        }

        val accessToken = extractIosJsonString(response.body, "access_token")
            ?.takeUnless { it.isBlank() }
            ?: return Result.failure(AuthException(AuthError.UNKNOWN))

        return Result.success(encodeIosGoogleAccessToken(accessToken))
    }
}

private data class IosGoogleSignInConfig(
    val clientId: String,
    val redirectScheme: String,
) {
    val redirectUri: String
        get() = "$redirectScheme:/oauth2redirect"
}

private class AuthenticationPresentationContextProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow
            ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
            ?: error("Unable to find an iOS window for Google Sign-In")
}

private fun NSURL.toAuthorizationCodeResult(expectedState: String): Result<String> {
    val params = buildMap {
        val items = NSURLComponents.componentsWithURL(
            this@toAuthorizationCodeResult,
            resolvingAgainstBaseURL = false
        )
            ?.queryItems()
            .orEmpty()
        items.forEach { item ->
            val queryItem = item as? platform.Foundation.NSURLQueryItem ?: return@forEach
            put(queryItem.name, queryItem.value ?: "")
        }
    }

    val error = params["error"]
    if (!error.isNullOrBlank()) {
        val authError = if (error == ACCESS_DENIED_ERROR) {
            AuthError.CANCELLED
        } else {
            AuthError.UNKNOWN
        }
        return Result.failure(AuthException(authError))
    }

    if (params["state"] != expectedState) {
        return Result.failure(AuthException(AuthError.UNKNOWN))
    }

    val authorizationCode = params["code"]
        ?.takeUnless { it.isBlank() }
        ?: return Result.failure(AuthException(AuthError.UNKNOWN))

    return Result.success(authorizationCode)
}

private fun NSError.toIosGoogleAuthException(): AuthException =
    if (code.toInt() == AS_WEB_AUTHENTICATION_CANCELLED_ERROR_CODE) {
        AuthException(AuthError.CANCELLED)
    } else {
        AuthException(AuthError.UNKNOWN)
    }

private fun CancellableContinuation<Result<String>>.resumeIfActive(result: Result<String>) {
    if (isActive) {
        resume(result)
    }
}

private fun buildAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    state: String,
    codeVerifier: String,
): String {
    val query = buildIosFormBody(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to AUTHORIZATION_RESPONSE_TYPE,
        "scope" to GOOGLE_SCOPES,
        "code_challenge" to codeVerifier,
        "code_challenge_method" to CODE_CHALLENGE_METHOD,
        "prompt" to GOOGLE_PROMPT,
        "state" to state,
    )
    return "$GOOGLE_AUTHORIZATION_ENDPOINT?$query"
}

private fun randomUrlSafeToken(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { pinned ->
        val result = SecRandomCopyBytes(
            rnd = kSecRandomDefault,
            count = byteCount.toULong(),
            bytes = pinned.addressOf(0),
        )
        check(result == 0) { "Unable to generate secure random bytes for Google Sign-In" }
    }
    return bytes.toBase64Url()
}

private fun ByteArray.toBase64Url(): String =
    toNSData()
        .base64EncodedStringWithOptions(0u)
        .replace("+", "-")
        .replace("/", "_")
        .trimEnd('=')

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }

private const val GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code"
private const val AUTHORIZATION_RESPONSE_TYPE = "code"
private const val GOOGLE_SCOPES = "openid email profile"
private const val GOOGLE_PROMPT = "select_account"
private const val CODE_CHALLENGE_METHOD = "plain"
private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
private const val ACCESS_DENIED_ERROR = "access_denied"
private const val AS_WEB_AUTHENTICATION_CANCELLED_ERROR_CODE = 1
