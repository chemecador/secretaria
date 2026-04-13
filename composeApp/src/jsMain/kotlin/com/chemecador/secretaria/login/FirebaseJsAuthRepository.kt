package com.chemecador.secretaria.login

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Date

private const val WEB_FIREBASE_API_KEY_ATTRIBUTE = "data-secretaria-firebase-api-key"

internal class FirebaseJsAuthRepository(
    private val apiKey: String,
    private val transport: FirebaseJsAuthTransport = BrowserFetchFirebaseJsAuthTransport(),
    private val nowProvider: () -> Double = { Date.now() },
) : AuthRepository, FirebaseJsIdTokenProvider {

    private var cachedSession: FirebaseJsAuthSession? = null

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

    override suspend fun loginWithGoogle(): Result<Unit> =
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
        } catch (_: Throwable) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode !in 200..299) {
            return Result.failure(AuthException(mapFirebaseRestError(response.body)))
        }

        val session = response.body.toFirebaseJsAuthSession(nowProvider())
            ?: return Result.failure(AuthException(AuthError.UNKNOWN))

        cachedSession = session
        return Result.success(Unit)
    }

    private suspend fun refreshSession(session: FirebaseJsAuthSession): FirebaseJsAuthSession {
        val response = transport.post(
            url = secureTokenUrl(),
            body = buildFormBody(
                "grant_type" to "refresh_token",
                "refresh_token" to session.refreshToken,
            ),
            contentType = FORM_CONTENT_TYPE,
        )

        if (response.statusCode !in 200..299) {
            error("Unable to refresh Firebase session")
        }

        val userId = extractJsonString(response.body, "user_id") ?: session.userId
        val idToken = extractJsonString(response.body, "id_token")
            ?: error("Missing id_token in refresh response")
        val refreshToken = extractJsonString(response.body, "refresh_token")
            ?: session.refreshToken
        val expiresInSeconds = extractJsonLong(response.body, "expires_in")
            ?: error("Missing expires_in in refresh response")

        return FirebaseJsAuthSession(
            userId = userId,
            idToken = idToken,
            refreshToken = refreshToken,
            expiresAtMillis = nowProvider() + expiresInSeconds * 1000.0,
        )
    }

    private fun urlFor(endpoint: String): String =
        "$IDENTITY_TOOLKIT_BASE_URL/$endpoint?key=$apiKey"

    private fun secureTokenUrl(): String =
        "$SECURE_TOKEN_BASE_URL/token?key=$apiKey"

    private companion object {
        const val IDENTITY_TOOLKIT_BASE_URL = "https://identitytoolkit.googleapis.com/v1"
        const val SECURE_TOKEN_BASE_URL = "https://securetoken.googleapis.com/v1"
        const val SIGN_IN_WITH_PASSWORD_ENDPOINT = "accounts:signInWithPassword"
        const val SIGN_UP_ENDPOINT = "accounts:signUp"
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
    }
}

internal fun resolveWebFirebaseApiKey(): String =
    document.documentElement
        ?.getAttribute(WEB_FIREBASE_API_KEY_ATTRIBUTE)
        ?.takeUnless { it.isBlank() }
        ?: error(
            "Missing Firebase API key for Web auth. " +
                "Set secretaria.firebaseApiKey in local.properties, " +
                "a Gradle property, or SECRETARIA_FIREBASE_API_KEY before building web.",
        )

internal interface FirebaseJsIdTokenProvider {
    suspend fun getFreshIdToken(): String
}

private data class FirebaseJsAuthSession(
    val userId: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Double,
) {
    fun isExpired(nowMillis: Double): Boolean =
        nowMillis >= expiresAtMillis - 30_000.0
}

private fun String.toFirebaseJsAuthSession(nowMillis: Double): FirebaseJsAuthSession? {
    val userId = extractJsonString(this, "localId") ?: return null
    val idToken = extractJsonString(this, "idToken") ?: return null
    val refreshToken = extractJsonString(this, "refreshToken") ?: return null
    val expiresInSeconds = extractJsonLong(this, "expiresIn")
        ?: extractJsonLong(this, "expires_in")
        ?: return null

    return FirebaseJsAuthSession(
        userId = userId,
        idToken = idToken,
        refreshToken = refreshToken,
        expiresAtMillis = nowMillis + expiresInSeconds * 1000.0,
    )
}

internal interface FirebaseJsAuthTransport {
    suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseJsAuthHttpResponse
}

internal data class FirebaseJsAuthHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal class BrowserFetchFirebaseJsAuthTransport : FirebaseJsAuthTransport {

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseJsAuthHttpResponse {
        val init = js("{}")
        init.method = "POST"
        init.body = body
        init.headers = js("{}")
        init.headers["Content-Type"] = contentType
        init.headers["Accept"] = "application/json"
        val response = window.fetch(url, init).await()
        return FirebaseJsAuthHttpResponse(
            statusCode = response.status.toInt(),
            body = response.text().await(),
        )
    }
}

private fun mapFirebaseRestError(responseBody: String): AuthError {
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

private fun extractFirebaseErrorMessage(json: String): String? {
    val nestedErrorMessage = Regex(
        "\"error\"\\s*:\\s*\\{[\\s\\S]*?\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    ).find(json)
        ?.groupValues
        ?.getOrNull(1)

    return nestedErrorMessage?.let(::unescapeJsonString)
        ?: extractJsonString(json, "message")
}

private fun extractJsonString(json: String, field: String): String? {
    val pattern = Regex(
        "\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    )
    return pattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::unescapeJsonString)
}

private fun extractJsonLong(json: String, field: String): Long? {
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

private fun buildJsonObject(vararg fields: Pair<String, Any>): String =
    fields.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
    ) { (name, value) ->
        "\"${escapeJsonString(name)}\":${value.toJsonLiteral()}"
    }

private fun buildFormBody(vararg fields: Pair<String, String>): String =
    fields.joinToString("&") { (name, value) ->
        "${encodeURIComponent(name)}=${encodeURIComponent(value)}"
    }

private external fun encodeURIComponent(value: String): String

private fun escapeJsonString(value: String): String = buildString {
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

private fun unescapeJsonString(value: String): String = buildString {
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
