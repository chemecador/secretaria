package com.chemecador.secretaria.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.logging.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberGoogleSignInController(serverClientId: String?): GoogleSignInController? {
    val clientId = remember { resolveGoogleDesktopClientId() } ?: return null
    val clientSecret = remember { resolveGoogleDesktopClientSecret() }
    return remember(clientId, clientSecret) {
        JvmGoogleSignInController(
            clientId = clientId,
            clientSecret = clientSecret,
        )
    }
}

private class JvmGoogleSignInController(
    private val clientId: String,
    private val clientSecret: String?,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val uriOpener: (URI) -> Boolean = ::openUriInBrowser,
) : GoogleSignInController {

    override suspend fun getIdToken(): Result<String> =
        withContext(Dispatchers.IO) {
            val callbackServer = OAuthLoopbackServer.start()
            try {
                val state = randomUrlSafeToken(24)
                val codeVerifier = randomUrlSafeToken(64)
                val codeChallenge = codeVerifier.toCodeChallenge()
                val authorizationUri = buildAuthorizationUri(
                    clientId = clientId,
                    redirectUri = callbackServer.redirectUri,
                    state = state,
                    codeChallenge = codeChallenge,
                )

                if (!uriOpener(authorizationUri)) {
                    logger.warning("Unable to open the system browser for Google Sign-In")
                    return@withContext Result.failure(AuthException(AuthError.NOT_SUPPORTED))
                }

                when (val callback = callbackServer.awaitCallback()) {
                    is OAuthCallback.Success -> {
                        if (callback.state != state) {
                            logger.warning("Google Sign-In state mismatch in JVM desktop flow")
                            return@withContext Result.failure(AuthException(AuthError.UNKNOWN))
                        }
                        exchangeCodeForIdToken(
                            code = callback.code,
                            codeVerifier = codeVerifier,
                            redirectUri = callbackServer.redirectUri,
                        )
                    }

                    is OAuthCallback.Error -> {
                        logger.warning("Google Sign-In callback returned error=${callback.error}")
                        val authError = if (callback.error == ACCESS_DENIED_ERROR) {
                            AuthError.CANCELLED
                        } else {
                            AuthError.UNKNOWN
                        }
                        Result.failure(AuthException(authError))
                    }
                }
            } finally {
                callbackServer.close()
            }
        }

    override suspend fun clearCredentialState() = Unit

    private fun exchangeCodeForIdToken(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): Result<String> {
        val response = try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(GOOGLE_TOKEN_ENDPOINT))
                .header("Content-Type", FORM_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        buildFormBody(
                            "client_id" to clientId,
                            "client_secret" to clientSecret.orEmpty(),
                            "code" to code,
                            "code_verifier" to codeVerifier,
                            "grant_type" to AUTHORIZATION_CODE_GRANT_TYPE,
                            "redirect_uri" to redirectUri,
                        ).removeBlankClientSecret(),
                        StandardCharsets.UTF_8,
                    ),
                )
                .build()
            httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
        } catch (exception: Exception) {
            logger.warning("Google token exchange failed: ${exception.message}")
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode() !in 200..299) {
            logger.warning(
                "Google token exchange failed with status=${response.statusCode()} body=${response.body()}",
            )
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        val idToken = extractJsonString(response.body(), "id_token")
            ?: run {
                logger.warning("Google token exchange succeeded without id_token")
                return Result.failure(AuthException(AuthError.UNKNOWN))
            }

        return Result.success(idToken)
    }
}

private class OAuthLoopbackServer private constructor(
    private val server: HttpServer,
    private val callbackResult: CompletableDeferred<OAuthCallback>,
) : AutoCloseable {

    val redirectUri: String
        get() = "http://127.0.0.1:${server.address.port}/callback"

    suspend fun awaitCallback(): OAuthCallback = callbackResult.await()

    override fun close() {
        server.stop(0)
    }

    companion object {
        fun start(): OAuthLoopbackServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val callbackResult = CompletableDeferred<OAuthCallback>()
            server.createContext("/callback") { exchange ->
                handleCallback(exchange, callbackResult)
            }
            server.start()
            return OAuthLoopbackServer(server, callbackResult)
        }

        private fun handleCallback(
            exchange: HttpExchange,
            callbackResult: CompletableDeferred<OAuthCallback>,
        ) {
            val params = parseUrlEncodedMap(exchange.requestURI.rawQuery)
            val callback = when {
                params["code"] != null -> OAuthCallback.Success(
                    code = params.getValue("code"),
                    state = params["state"],
                )

                else -> OAuthCallback.Error(params["error"] ?: "unknown_error")
            }

            callbackResult.complete(callback)
            val html = """
                <html>
                  <head><meta charset="utf-8"/></head>
                  <body>
                    <h2>Ya puedes volver a Secretaria</h2>
                    <p>La autenticación de Google se ha completado. Puedes cerrar esta ventana.</p>
                  </body>
                </html>
            """.trimIndent()
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            OutputStreamWriter(exchange.responseBody, StandardCharsets.UTF_8).use { writer ->
                writer.write(html)
            }
            exchange.close()
        }
    }
}

private sealed interface OAuthCallback {
    data class Success(val code: String, val state: String?) : OAuthCallback
    data class Error(val error: String) : OAuthCallback
}

private fun buildAuthorizationUri(
    clientId: String,
    redirectUri: String,
    state: String,
    codeChallenge: String,
): URI {
    val query = buildFormBody(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to AUTHORIZATION_RESPONSE_TYPE,
        "scope" to GOOGLE_IDENTITY_SCOPES,
        "code_challenge" to codeChallenge,
        "code_challenge_method" to CODE_CHALLENGE_METHOD,
        "access_type" to OFFLINE_ACCESS_TYPE,
        "state" to state,
        "prompt" to SELECT_ACCOUNT_PROMPT,
    )
    return URI.create("$GOOGLE_AUTHORIZATION_ENDPOINT?$query")
}

private fun parseUrlEncodedMap(rawQuery: String?): Map<String, String> =
    rawQuery
        ?.split("&")
        ?.asSequence()
        ?.filter { it.isNotBlank() }
        ?.map { pair ->
            val separatorIndex = pair.indexOf('=')
            if (separatorIndex < 0) {
                urlDecode(pair) to ""
            } else {
                urlDecode(pair.substring(0, separatorIndex)) to
                    urlDecode(pair.substring(separatorIndex + 1))
            }
        }
        ?.toMap()
        ?: emptyMap()

private fun String.removeBlankClientSecret(): String =
    split("&")
        .filterNot { it == "client_secret=" }
        .joinToString("&")

private fun randomUrlSafeToken(size: Int): String {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
}

private fun String.toCodeChallenge(): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(toByteArray(StandardCharsets.US_ASCII)))

private fun urlDecode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

private fun openUriInBrowser(uri: URI): Boolean =
    runCatching {
        if (!Desktop.isDesktopSupported()) {
            false
        } else {
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                false
            } else {
                desktop.browse(uri)
                true
            }
        }
    }.getOrDefault(false)

private const val GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val AUTHORIZATION_RESPONSE_TYPE = "code"
private const val AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code"
private const val CODE_CHALLENGE_METHOD = "S256"
private const val OFFLINE_ACCESS_TYPE = "offline"
private const val SELECT_ACCOUNT_PROMPT = "select_account"
private const val ACCESS_DENIED_ERROR = "access_denied"
private const val GOOGLE_IDENTITY_SCOPES = "openid email profile"
private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
private const val JSON_CONTENT_TYPE = "application/json"
private val logger: Logger = Logger.getLogger(JvmGoogleSignInController::class.java.name)
