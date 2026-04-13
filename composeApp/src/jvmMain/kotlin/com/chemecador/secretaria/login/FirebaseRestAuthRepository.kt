package com.chemecador.secretaria.login

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class FirebaseRestAuthRepository(
    private val apiKey: String,
    private val transport: FirebaseAuthTransport = HttpClientFirebaseAuthTransport(),
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : AuthRepository, FirebaseIdTokenProvider {

    private var cachedSession: FirebaseAuthSession? = null

    override val currentUserId: String?
        get() = cachedSession?.userId

    override suspend fun login(email: String, password: String): Result<Unit> =
        authenticate(
            endpoint = SIGN_IN_WITH_PASSWORD_ENDPOINT,
            requestBody = buildJsonObject(
                "email" to email,
                "password" to password,
                "returnSecureToken" to true,
            ),
        )

    override suspend fun signup(email: String, password: String): Result<Unit> =
        authenticate(
            endpoint = SIGN_UP_ENDPOINT,
            requestBody = buildJsonObject(
                "email" to email,
                "password" to password,
                "returnSecureToken" to true,
            ),
        )

    override suspend fun loginWithGoogle(idToken: String?): Result<Unit> =
        Result.failure(AuthException(AuthError.NOT_SUPPORTED))

    override suspend fun loginAsGuest(): Result<Unit> =
        authenticate(
            endpoint = SIGN_UP_ENDPOINT,
            requestBody = buildJsonObject(
                "returnSecureToken" to true,
            ),
        )

    override suspend fun logout(): Result<Unit> {
        cachedSession = null
        return Result.success(Unit)
    }

    override suspend fun getFreshIdToken(): String {
        val currentSession = cachedSession
            ?: error("User not logged in")

        if (!currentSession.isExpired(nowProvider())) {
            return currentSession.idToken
        }

        val refreshedSession = refreshSession(currentSession)
        cachedSession = refreshedSession
        return refreshedSession.idToken
    }

    private suspend fun authenticate(endpoint: String, requestBody: String): Result<Unit> {
        val response = try {
            transport.post(
                url = urlFor(endpoint),
                body = requestBody,
                contentType = JSON_CONTENT_TYPE,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode !in 200..299) {
            return Result.failure(AuthException(mapFirebaseRestError(response.body)))
        }

        val session = response.body.toFirebaseAuthSession(nowProvider())
            ?: return Result.failure(AuthException(AuthError.UNKNOWN))

        cachedSession = session
        return Result.success(Unit)
    }

    private suspend fun refreshSession(session: FirebaseAuthSession): FirebaseAuthSession {
        val response = try {
            transport.post(
                url = secureTokenUrl(),
                body = buildFormBody(
                    "grant_type" to "refresh_token",
                    "refresh_token" to session.refreshToken,
                ),
                contentType = FORM_CONTENT_TYPE,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to refresh Firebase session", exception)
        }

        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Unable to refresh Firebase session")
        }

        val userId = extractJsonString(response.body, "user_id") ?: session.userId
        val idToken = extractJsonString(response.body, "id_token")
            ?: throw IllegalStateException("Missing id_token in refresh response")
        val refreshToken = extractJsonString(response.body, "refresh_token") ?: session.refreshToken
        val expiresInSeconds = extractJsonLong(response.body, "expires_in")
            ?: throw IllegalStateException("Missing expires_in in refresh response")

        return FirebaseAuthSession(
            userId = userId,
            idToken = idToken,
            refreshToken = refreshToken,
            expiresAt = nowProvider() + expiresInSeconds.seconds,
        )
    }

    private fun urlFor(endpoint: String): String {
        val encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        return "$IDENTITY_TOOLKIT_BASE_URL/$endpoint?key=$encodedApiKey"
    }

    private fun secureTokenUrl(): String {
        val encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        return "$SECURE_TOKEN_BASE_URL/token?key=$encodedApiKey"
    }

    private companion object {
        const val IDENTITY_TOOLKIT_BASE_URL = "https://identitytoolkit.googleapis.com/v1"
        const val SECURE_TOKEN_BASE_URL = "https://securetoken.googleapis.com/v1"
        const val SIGN_IN_WITH_PASSWORD_ENDPOINT = "accounts:signInWithPassword"
        const val SIGN_UP_ENDPOINT = "accounts:signUp"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

internal interface FirebaseIdTokenProvider {
    suspend fun getFreshIdToken(): String
}

private data class FirebaseAuthSession(
    val userId: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean =
        now >= expiresAt - 30.seconds
}

private fun String.toFirebaseAuthSession(now: Instant): FirebaseAuthSession? {
    val userId = extractJsonString(this, "localId") ?: return null
    val idToken = extractJsonString(this, "idToken") ?: return null
    val refreshToken = extractJsonString(this, "refreshToken") ?: return null
    val expiresInSeconds = extractJsonLong(this, "expiresIn")
        ?: extractJsonLong(this, "expires_in")
        ?: return null

    return FirebaseAuthSession(
        userId = userId,
        idToken = idToken,
        refreshToken = refreshToken,
        expiresAt = now + expiresInSeconds.seconds,
    )
}

internal interface FirebaseAuthTransport {
    suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseAuthHttpResponse
}

internal data class FirebaseAuthHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal class HttpClientFirebaseAuthTransport(
    private val client: HttpClient = HttpClient.newHttpClient(),
) : FirebaseAuthTransport {

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseAuthHttpResponse =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
            FirebaseAuthHttpResponse(
                statusCode = response.statusCode(),
                body = response.body(),
            )
        }
}

internal fun mapFirebaseRestError(responseBody: String): AuthError {
    val errorMessage = extractFirebaseErrorMessage(responseBody) ?: return AuthError.UNKNOWN

    return when {
        errorMessage == "EMAIL_NOT_FOUND" -> AuthError.INVALID_USER
        errorMessage == "INVALID_PASSWORD" -> AuthError.WRONG_PASSWORD
        errorMessage == "INVALID_LOGIN_CREDENTIALS" -> AuthError.WRONG_PASSWORD
        errorMessage == "EMAIL_EXISTS" -> AuthError.USER_ALREADY_EXISTS
        errorMessage.startsWith("WEAK_PASSWORD") -> AuthError.WEAK_PASSWORD
        errorMessage == "INVALID_EMAIL" -> AuthError.INVALID_EMAIL
        errorMessage == "MISSING_EMAIL" -> AuthError.INVALID_EMAIL
        else -> AuthError.UNKNOWN
    }
}

internal fun extractFirebaseErrorMessage(json: String): String? {
    val nestedErrorMessage = Regex(
        "\"error\"\\s*:\\s*\\{[\\s\\S]*?\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    ).find(json)
        ?.groupValues
        ?.getOrNull(1)

    return nestedErrorMessage?.let(::unescapeJsonString)
        ?: extractJsonString(json, "message")
}

internal fun extractJsonString(json: String, field: String): String? {
    val pattern = Regex(
        "\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    )
    return pattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::unescapeJsonString)
}

internal fun extractJsonLong(json: String, field: String): Long? {
    val stringLiteralPattern = Regex(
        "\"${Regex.escape(field)}\"\\s*:\\s*\"(-?\\d+)\"",
    )
    val numericPattern = Regex(
        "\"${Regex.escape(field)}\"\\s*:\\s*(-?\\d+)",
    )
    return stringLiteralPattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?: numericPattern.find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
}

internal fun buildJsonObject(vararg fields: Pair<String, Any>): String =
    fields.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
    ) { (name, value) ->
        "\"${escapeJsonString(name)}\":${value.toJsonLiteral()}"
    }

internal fun escapeJsonString(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}

internal fun buildFormBody(vararg fields: Pair<String, String>): String =
    fields.joinToString("&") { (name, value) ->
        "${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${
            URLEncoder.encode(value, StandardCharsets.UTF_8)
        }"
    }

internal fun unescapeJsonString(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character != '\\' || index == value.lastIndex) {
            append(character)
            index += 1
            continue
        }

        when (val escaped = value[index + 1]) {
            '"', '\\', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val unicodeHex = value.substring(index + 2, index + 6)
                append(unicodeHex.toInt(16).toChar())
                index += 4
            }
            else -> append(escaped)
        }
        index += 2
    }
}

private fun Any.toJsonLiteral(): String = when (this) {
    is String -> "\"${escapeJsonString(this)}\""
    is Boolean, is Number -> toString()
    else -> error("Unsupported JSON literal type: ${this::class.simpleName}")
}
