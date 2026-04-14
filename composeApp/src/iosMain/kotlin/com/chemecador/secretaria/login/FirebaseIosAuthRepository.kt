@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.chemecador.secretaria.login

import com.chemecador.secretaria.requireIosGoogleServiceInfoString
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.addValue
import platform.Foundation.appendData
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class FirebaseIosAuthRepository(
    private val apiKey: String,
    private val transport: FirebaseIosAuthTransport = NSURLSessionFirebaseIosAuthTransport(),
    private val nowProvider: () -> Instant = { Clock.System.now() },
    private val sessionStore: SessionStore = NoOpSessionStore,
) : AuthRepository, FirebaseIosIdTokenProvider {

    private var cachedSession: FirebaseIosAuthSession? = null

    override val currentUserId: String?
        get() = cachedSession?.userId

    override val currentUserEmail: String?
        get() = cachedSession?.email

    override suspend fun login(email: String, password: String): Result<Unit> =
        authenticate(
            endpoint = SIGN_IN_WITH_PASSWORD_ENDPOINT,
            requestBody = buildIosJsonObject(
                "email" to email,
                "password" to password,
                "returnSecureToken" to true,
            ),
        )

    override suspend fun signup(email: String, password: String): Result<Unit> =
        authenticate(
            endpoint = SIGN_UP_ENDPOINT,
            requestBody = buildIosJsonObject(
                "email" to email,
                "password" to password,
                "returnSecureToken" to true,
            ),
        )

    override suspend fun loginWithGoogle(idToken: String?): Result<Unit> {
        val googleToken = idToken
            ?.takeUnless { it.isBlank() }
            ?: return Result.failure(AuthException(AuthError.NOT_SUPPORTED))
        val (tokenField, tokenValue) = googleToken.toIosGooglePostBodyField()

        return authenticate(
            endpoint = SIGN_IN_WITH_IDP_ENDPOINT,
            requestBody = buildIosJsonObject(
                "requestUri" to GOOGLE_SIGN_IN_REQUEST_URI,
                "postBody" to buildIosFormBody(
                    tokenField to tokenValue,
                    "providerId" to GOOGLE_PROVIDER_ID,
                ),
                "returnSecureToken" to true,
                "returnIdpCredential" to true,
            ),
        )
    }

    override suspend fun loginAsGuest(): Result<Unit> =
        authenticate(
            endpoint = SIGN_UP_ENDPOINT,
            requestBody = buildIosJsonObject(
                "returnSecureToken" to true,
            ),
        )

    override suspend fun logout(): Result<Unit> {
        cachedSession = null
        sessionStore.clear()
        return Result.success(Unit)
    }

    override suspend fun restoreSession(): Result<Boolean> {
        val persisted = sessionStore.load() ?: return Result.success(false)
        val loaded = persisted.toFirebaseIosAuthSession()
        cachedSession = loaded

        if (!loaded.isExpired(nowProvider())) {
            return Result.success(true)
        }

        val refreshed = try {
            refreshSession(loaded)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cachedSession = null
            sessionStore.clear()
            return Result.success(false)
        }

        cachedSession = refreshed
        sessionStore.save(refreshed.toPersisted())
        return Result.success(true)
    }

    override suspend fun getFreshIdToken(): String {
        val currentSession = cachedSession
            ?: error("User not logged in")

        if (!currentSession.isExpired(nowProvider())) {
            return currentSession.idToken
        }

        val refreshedSession = refreshSession(currentSession)
        cachedSession = refreshedSession
        sessionStore.save(refreshedSession.toPersisted())
        return refreshedSession.idToken
    }

    private suspend fun authenticate(endpoint: String, requestBody: String): Result<Unit> {
        val response = try {
            transport.post(
                url = urlFor(endpoint),
                body = requestBody,
                contentType = JSON_CONTENT_TYPE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.failure(AuthException(AuthError.UNKNOWN))
        }

        if (response.statusCode !in 200..299) {
            return Result.failure(AuthException(mapIosFirebaseRestError(response.body)))
        }

        val session = response.body.toFirebaseIosAuthSession(nowProvider())
            ?: return Result.failure(AuthException(AuthError.UNKNOWN))

        cachedSession = session
        sessionStore.save(session.toPersisted())
        return Result.success(Unit)
    }

    private suspend fun refreshSession(session: FirebaseIosAuthSession): FirebaseIosAuthSession {
        val response = try {
            transport.post(
                url = secureTokenUrl(),
                body = buildIosFormBody(
                    "grant_type" to "refresh_token",
                    "refresh_token" to session.refreshToken,
                ),
                contentType = FORM_CONTENT_TYPE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Unable to refresh Firebase session", e)
        }

        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Unable to refresh Firebase session")
        }

        val userId = extractIosJsonString(response.body, "user_id") ?: session.userId
        val idToken = extractIosJsonString(response.body, "id_token")
            ?: throw IllegalStateException("Missing id_token in refresh response")
        val refreshToken = extractIosJsonString(response.body, "refresh_token")
            ?: session.refreshToken
        val expiresInSeconds = extractIosJsonLong(response.body, "expires_in")
            ?: throw IllegalStateException("Missing expires_in in refresh response")

        return FirebaseIosAuthSession(
            userId = userId,
            email = session.email,
            idToken = idToken,
            refreshToken = refreshToken,
            expiresAt = nowProvider() + expiresInSeconds.seconds,
        )
    }

    private fun urlFor(endpoint: String): String =
        "$IDENTITY_TOOLKIT_BASE_URL/$endpoint?key=${percentEncode(apiKey)}"

    private fun secureTokenUrl(): String =
        "$SECURE_TOKEN_BASE_URL/token?key=${percentEncode(apiKey)}"

    private companion object {
        const val IDENTITY_TOOLKIT_BASE_URL = "https://identitytoolkit.googleapis.com/v1"
        const val SECURE_TOKEN_BASE_URL = "https://securetoken.googleapis.com/v1"
        const val SIGN_IN_WITH_IDP_ENDPOINT = "accounts:signInWithIdp"
        const val SIGN_IN_WITH_PASSWORD_ENDPOINT = "accounts:signInWithPassword"
        const val SIGN_UP_ENDPOINT = "accounts:signUp"
        const val GOOGLE_PROVIDER_ID = "google.com"
        const val GOOGLE_SIGN_IN_REQUEST_URI = "http://localhost"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

internal interface FirebaseIosIdTokenProvider {
    suspend fun getFreshIdToken(): String
}

private data class FirebaseIosAuthSession(
    val userId: String,
    val email: String?,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean =
        now >= expiresAt - 30.seconds
}

private fun FirebaseIosAuthSession.toPersisted(): PersistedAuthSession =
    PersistedAuthSession(
        userId = userId,
        email = email,
        idToken = idToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = expiresAt.epochSeconds,
    )

private fun PersistedAuthSession.toFirebaseIosAuthSession(): FirebaseIosAuthSession =
    FirebaseIosAuthSession(
        userId = userId,
        email = email,
        idToken = idToken,
        refreshToken = refreshToken,
        expiresAt = Instant.fromEpochSeconds(expiresAtEpochSeconds),
    )

private fun String.toFirebaseIosAuthSession(now: Instant): FirebaseIosAuthSession? {
    val userId = extractIosJsonString(this, "localId") ?: return null
    val idToken = extractIosJsonString(this, "idToken") ?: return null
    val refreshToken = extractIosJsonString(this, "refreshToken") ?: return null
    val expiresInSeconds = extractIosJsonLong(this, "expiresIn")
        ?: extractIosJsonLong(this, "expires_in")
        ?: return null
    val email = extractIosJsonString(this, "email")?.takeUnless { it.isBlank() }

    return FirebaseIosAuthSession(
        userId = userId,
        email = email,
        idToken = idToken,
        refreshToken = refreshToken,
        expiresAt = now + expiresInSeconds.seconds,
    )
}

// --- Transport ---

internal interface FirebaseIosAuthTransport {
    suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseIosAuthHttpResponse
}

internal data class FirebaseIosAuthHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal class NSURLSessionFirebaseIosAuthTransport : FirebaseIosAuthTransport {

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
    ): FirebaseIosAuthHttpResponse = suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url)
            ?: throw IllegalArgumentException("Invalid URL: $url")
        val request = NSMutableURLRequest(uRL = nsUrl)
        request.HTTPMethod = "POST"
        @Suppress("CAST_NEVER_SUCCEEDS")
        request.HTTPBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        request.addValue(contentType, forHTTPHeaderField = "Content-Type")
        request.addValue("application/json", forHTTPHeaderField = "Accept")

        val delegate = PostTaskDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(
            configuration = NSURLSessionConfiguration.defaultSessionConfiguration,
            delegate = delegate,
            delegateQueue = null,
        )

        val task = session.dataTaskWithRequest(request)
        continuation.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
        task.resume()
    }
}

private class PostTaskDelegate(
    private val continuation: CancellableContinuation<FirebaseIosAuthHttpResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {

    private val receivedData = NSMutableData()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        receivedData.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        session.finishTasksAndInvalidate()

        if (didCompleteWithError != null) {
            continuation.resumeWithException(
                Exception(didCompleteWithError.localizedDescription),
            )
            return
        }

        val httpResponse = task.response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode?.toInt() ?: 0
        val responseBody = receivedData.toKotlinString()

        continuation.resume(FirebaseIosAuthHttpResponse(statusCode, responseBody))
    }
}

// --- Plist config reader ---

internal fun resolveIosFirebaseApiKey(): String =
    requireIosGoogleServiceInfoString("API_KEY")

internal fun resolveIosGoogleClientId(): String =
    requireIosGoogleServiceInfoString("CLIENT_ID")

internal fun resolveIosGoogleReversedClientId(): String =
    requireIosGoogleServiceInfoString("REVERSED_CLIENT_ID")

// --- NSData helpers ---

private fun NSData.toKotlinString(): String {
    val length = this.length.toInt()
    if (length == 0) return ""
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes.decodeToString()
}

// --- JSON / form helpers ---

private fun mapIosFirebaseRestError(responseBody: String): AuthError {
    val errorMessage = extractIosFirebaseErrorMessage(responseBody) ?: return AuthError.UNKNOWN

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

private fun extractIosFirebaseErrorMessage(json: String): String? {
    val nestedErrorMessage = Regex(
        "\"error\"\\s*:\\s*\\{[\\s\\S]*?\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    ).find(json)
        ?.groupValues
        ?.getOrNull(1)

    return nestedErrorMessage?.let(::unescapeIosJsonString)
        ?: extractIosJsonString(json, "message")
}

internal fun extractIosJsonString(json: String, field: String): String? {
    val pattern = Regex(
        "\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
    )
    return pattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::unescapeIosJsonString)
}

internal fun extractIosJsonLong(json: String, field: String): Long? {
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

internal fun buildIosJsonObject(vararg fields: Pair<String, Any>): String =
    fields.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ",",
    ) { (name, value) ->
        "\"${escapeIosJsonString(name)}\":${value.toIosJsonLiteral()}"
    }

internal fun buildIosFormBody(vararg fields: Pair<String, String>): String =
    fields.joinToString("&") { (name, value) ->
        "${percentEncode(name)}=${percentEncode(value)}"
    }

internal fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append(byte.toUByte().toString(16).uppercase().padStart(2, '0'))
        }
    }
}

internal const val IOS_GOOGLE_ACCESS_TOKEN_PREFIX = "google_access_token:"

internal fun encodeIosGoogleAccessToken(accessToken: String): String =
    "$IOS_GOOGLE_ACCESS_TOKEN_PREFIX$accessToken"

private fun String.toIosGooglePostBodyField(): Pair<String, String> =
    if (startsWith(IOS_GOOGLE_ACCESS_TOKEN_PREFIX)) {
        "access_token" to removePrefix(IOS_GOOGLE_ACCESS_TOKEN_PREFIX)
    } else {
        "id_token" to this
    }

private fun escapeIosJsonString(value: String): String = buildString {
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

private fun unescapeIosJsonString(value: String): String = buildString {
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

private fun Any.toIosJsonLiteral(): String = when (this) {
    is String -> "\"${escapeIosJsonString(this)}\""
    is Boolean, is Number -> toString()
    else -> error("Unsupported JSON literal type: ${this::class.simpleName}")
}
