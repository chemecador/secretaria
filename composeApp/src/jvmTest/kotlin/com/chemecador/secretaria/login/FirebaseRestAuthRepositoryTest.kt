package com.chemecador.secretaria.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FirebaseRestAuthRepositoryTest {

    @Test
    fun login_successStoresCurrentUserId() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            response = FirebaseAuthHttpResponse(
                statusCode = 200,
                body = """{"localId":"user-123"}""",
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
    }

    @Test
    fun signup_successStoresCurrentUserId() = runTest {
        val transport = RecordingFirebaseAuthTransport(
            response = FirebaseAuthHttpResponse(
                statusCode = 200,
                body = """{"localId":"new-user"}""",
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
            response = FirebaseAuthHttpResponse(
                statusCode = 200,
                body = """{"localId":"guest-user"}""",
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
            response = FirebaseAuthHttpResponse(
                statusCode = 200,
                body = """{"kind":"identitytoolkit#VerifyPasswordResponse"}""",
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
            response = FirebaseAuthHttpResponse(
                statusCode = 500,
                body = """{"status":"server-error"}""",
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
        )

        assertEquals("property-key", key)
    }

    @Test
    fun resolveFirebaseApiKey_failsWithClearMessageWhenMissing() {
        val error = assertFailsWith<IllegalStateException> {
            resolveFirebaseApiKey(
                propertyProvider = { null },
                environmentProvider = { null },
            )
        }

        assertTrue(error.message!!.contains("secretaria.firebaseApiKey"))
        assertTrue(error.message!!.contains("SECRETARIA_FIREBASE_API_KEY"))
    }

    @Test
    fun loginWithGoogle_isNotSupported() = runTest {
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = RecordingFirebaseAuthTransport(
                response = FirebaseAuthHttpResponse(200, """{"localId":"unused"}"""),
            ),
        )

        val result = repository.loginWithGoogle()

        assertAuthError(AuthError.NOT_SUPPORTED, result)
    }

    private fun repositoryForError(errorMessage: String): FirebaseRestAuthRepository =
        FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = RecordingFirebaseAuthTransport(
                response = FirebaseAuthHttpResponse(
                    statusCode = 400,
                    body = """{"error":{"code":400,"message":"$errorMessage"}}""",
                ),
            ),
        )

    private fun assertAuthError(expected: AuthError, result: Result<Unit>) {
        assertTrue(result.isFailure)
        val error = assertIs<AuthException>(result.exceptionOrNull())
        assertEquals(expected, error.error)
    }

    private class RecordingFirebaseAuthTransport(
        private val response: FirebaseAuthHttpResponse,
    ) : FirebaseAuthTransport {

        val requests = mutableListOf<Request>()

        override suspend fun post(url: String, body: String): FirebaseAuthHttpResponse {
            requests += Request(url, body)
            return response
        }
    }

    private data class Request(
        val url: String,
        val body: String,
    )
}
