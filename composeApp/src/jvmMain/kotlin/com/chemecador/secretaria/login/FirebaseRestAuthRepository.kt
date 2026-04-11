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

internal class FirebaseRestAuthRepository(
    private val apiKey: String,
    private val transport: FirebaseAuthTransport = HttpClientFirebaseAuthTransport(),
) : AuthRepository {

    private var cachedUserId: String? = null

    override val currentUserId: String?
        get() = cachedUserId

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

    private suspend fun authenticate(endpoint: String, requestBody: String): Result<Unit> {
        val response = try {
            transport.post(urlFor(endpoint), requestBody)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode !in 200..299) {
            return Result.failure(AuthException(mapFirebaseRestError(response.body)))
        }

        val userId = extractJsonString(response.body, "localId")
            ?: return Result.failure(AuthException(AuthError.UNKNOWN))

        cachedUserId = userId
        return Result.success(Unit)
    }

    private fun urlFor(endpoint: String): String {
        val encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        return "$BASE_URL/$endpoint?key=$encodedApiKey"
    }

    private companion object {
        const val BASE_URL = "https://identitytoolkit.googleapis.com/v1"
        const val SIGN_IN_WITH_PASSWORD_ENDPOINT = "accounts:signInWithPassword"
        const val SIGN_UP_ENDPOINT = "accounts:signUp"
    }
}

internal fun interface FirebaseAuthTransport {
    suspend fun post(url: String, body: String): FirebaseAuthHttpResponse
}

internal data class FirebaseAuthHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal class HttpClientFirebaseAuthTransport(
    private val client: HttpClient = HttpClient.newHttpClient(),
) : FirebaseAuthTransport {

    override suspend fun post(url: String, body: String): FirebaseAuthHttpResponse =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
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
