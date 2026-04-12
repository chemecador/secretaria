package com.chemecador.secretaria.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class FirebaseRestAuthRepositoryTest {

    @Test
    fun login_successStoresCurrentUserId() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = successBody(
                        userId = "user-123",
                        idToken = "id-token-1",
                        refreshToken = "refresh-token-1",
                    ),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "api key",
            transport = transport,
        )

        val result = repository.login("alex@example.com", "clave123")

        assertTrue(result.isSuccess)
        assertEquals("user-123", repository.currentUserId)
        assertEquals(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=api+key",
            transport.requests.single().url,
        )
        assertEquals(
            """{"email":"alex@example.com","password":"clave123","returnSecureToken":true}""",
            transport.requests.single().body,
        )
        assertEquals(
            "application/json; charset=utf-8",
            transport.requests.single().contentType,
        )
    }

    @Test
    fun signup_successStoresCurrentUserId() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = successBody(
                        userId = "new-user",
                        idToken = "id-token-2",
                        refreshToken = "refresh-token-2",
                    ),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
        )

        val result = repository.signup("new@example.com", "secreta")

        assertTrue(result.isSuccess)
        assertEquals("new-user", repository.currentUserId)
        assertEquals(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=test-key",
            transport.requests.single().url,
        )
        assertEquals(
            """{"email":"new@example.com","password":"secreta","returnSecureToken":true}""",
            transport.requests.single().body,
        )
    }

    @Test
    fun loginAsGuest_successStoresCurrentUserId() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = successBody(
                        userId = "guest-user",
                        idToken = "guest-id-token",
                        refreshToken = "guest-refresh-token",
                    ),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
        )

        val result = repository.loginAsGuest()

        assertTrue(result.isSuccess)
        assertEquals("guest-user", repository.currentUserId)
        assertEquals(
            """{"returnSecureToken":true}""",
            transport.requests.single().body,
        )
    }

    @Test
    fun getFreshIdToken_refreshesExpiredSession() = runTest {
        var now = Instant.parse("2026-04-12T10:00:00Z")
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = successBody(
                        userId = "user-123",
                        idToken = "stale-token",
                        refreshToken = "refresh-token-1",
                        expiresIn = 60,
                    ),
                ),
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "user_id":"user-123",
                          "id_token":"fresh-token",
                          "refresh_token":"refresh-token-2",
                          "expires_in":"3600"
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
            nowProvider = { now },
        )

        repository.login("alex@example.com", "secreta")
        now = Instant.parse("2026-04-12T10:01:01Z")
        val refreshedToken = repository.getFreshIdToken()

        assertEquals("fresh-token", refreshedToken)
        assertEquals("user-123", repository.currentUserId)
        assertEquals(2, transport.requests.size)
        assertEquals(
            "https://securetoken.googleapis.com/v1/token?key=test-key",
            transport.requests[1].url,
        )
        assertEquals(
            "grant_type=refresh_token&refresh_token=refresh-token-1",
            transport.requests[1].body,
        )
        assertEquals(
            "application/x-www-form-urlencoded; charset=utf-8",
            transport.requests[1].contentType,
        )
    }

    @Test
    fun signup_emailExistsMapsToUserAlreadyExists() = runTest {
        val repository = repositoryForError("EMAIL_EXISTS")

        val result = repository.signup("alex@example.com", "secreta")

        assertAuthError(AuthError.USER_ALREADY_EXISTS, result)
        assertNull(repository.currentUserId)
    }

    @Test
    fun signup_weakPasswordMapsToWeakPassword() = runTest {
        val repository = repositoryForError("WEAK_PASSWORD : Password should be at least 6 characters")

        val result = repository.signup("alex@example.com", "123")

        assertAuthError(AuthError.WEAK_PASSWORD, result)
    }

    @Test
    fun signup_invalidEmailMapsToInvalidEmail() = runTest {
        val repository = repositoryForError("INVALID_EMAIL")

        val result = repository.signup("correo-invalido", "secreta")

        assertAuthError(AuthError.INVALID_EMAIL, result)
    }

    @Test
    fun signup_missingEmailMapsToInvalidEmail() = runTest {
        val repository = repositoryForError("MISSING_EMAIL")

        val result = repository.signup("", "secreta")

        assertAuthError(AuthError.INVALID_EMAIL, result)
    }

    @Test
    fun login_invalidLoginCredentialsMapsToWrongPassword() = runTest {
        val repository = repositoryForError("INVALID_LOGIN_CREDENTIALS")

        val result = repository.login("alex@example.com", "mala")

        assertAuthError(AuthError.WRONG_PASSWORD, result)
    }

    @Test
    fun login_unexpectedSuccessBodyMapsToUnknown() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = """{"kind":"identitytoolkit#VerifyPasswordResponse"}""",
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
        )

        val result = repository.login("alex@example.com", "secreta")

        assertAuthError(AuthError.UNKNOWN, result)
        assertNull(repository.currentUserId)
    }

    @Test
    fun login_unexpectedStatusMapsToUnknown() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            responses = listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 500,
                    body = """{"status":"server-error"}""",
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
        )

        val result = repository.login("alex@example.com", "secreta")

        assertAuthError(AuthError.UNKNOWN, result)
    }

    @Test
    fun resolveFirebaseApiKey_prefersSystemPropertyThenEnv() {
        val key = resolveFirebaseApiKey(
            propertyProvider = { name ->
                if (name == "secretaria.firebaseApiKey") "property-key" else null
            },
            environmentProvider = { "env-key" },
            localPropertiesProvider = { "local-key" },
        )

        assertEquals("property-key", key)
    }

    @Test
    fun resolveFirebaseApiKey_fallsBackToLocalProperties() {
        val key = resolveFirebaseApiKey(
            propertyProvider = { null },
            environmentProvider = { null },
            localPropertiesProvider = { name ->
                if (name == "secretaria.firebaseApiKey") "local-key" else null
            },
        )

        assertEquals("local-key", key)
    }

    @Test
    fun resolveFirebaseApiKey_failsWithClearMessageWhenMissing() {
        val error = assertFailsWith<IllegalStateException> {
            resolveFirebaseApiKey(
                propertyProvider = { null },
                environmentProvider = { null },
                localPropertiesProvider = { null },
            )
        }

        assertTrue(error.message!!.contains("secretaria.firebaseApiKey"))
        assertTrue(error.message!!.contains("SECRETARIA_FIREBASE_API_KEY"))
        assertTrue(error.message!!.contains("local.properties"))
    }

    @Test
    fun loginWithGoogle_isNotSupported() = runTest {
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = RecordingFirebaseAuthTransport(
                responses = listOf(
                    FirebaseAuthHttpResponse(
                        statusCode = 200,
                        body = successBody(
                            userId = "unused",
                            idToken = "unused-token",
                            refreshToken = "unused-refresh",
                        ),
                    ),
                ),
            ),
        )

        val result = repository.loginWithGoogle()

        assertAuthError(AuthError.NOT_SUPPORTED, result)
    }

    private fun repositoryForError(errorMessage: String): FirebaseRestAuthRepository =
        FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = RecordingFirebaseAuthTransport(
                responses = listOf(
                    FirebaseAuthHttpResponse(
                        statusCode = 400,
                        body = """{"error":{"code":400,"message":"$errorMessage"}}""",
                    ),
                ),
            ),
        )

    private fun successBody(
        userId: String,
        idToken: String,
        refreshToken: String,
        expiresIn: Long = 3600,
    ): String =
        """
            {
              "localId":"$userId",
              "idToken":"$idToken",
              "refreshToken":"$refreshToken",
              "expiresIn":"$expiresIn"
            }
        """.trimIndent()

    private fun assertAuthError(expected: AuthError, result: Result<Unit>) {
        assertTrue(result.isFailure)
        val error = assertIs<AuthException>(result.exceptionOrNull())
        assertEquals(expected, error.error)
    }

    private class RecordingFirebaseAuthTransport(
        responses: List<FirebaseAuthHttpResponse>,
    ) : FirebaseAuthTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<Request>()

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
        ): FirebaseAuthHttpResponse {
            requests += Request(url, body, contentType)
            return pendingResponses.removeFirst()
        }
    }

    private data class Request(
        val url: String,
        val body: String,
        val contentType: String,
    )
}
