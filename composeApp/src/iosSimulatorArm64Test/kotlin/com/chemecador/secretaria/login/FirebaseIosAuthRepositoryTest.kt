package com.chemecador.secretaria.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class FirebaseIosAuthRepositoryTest {

    @Test
    fun loginWithGoogle_usesIdTokenForFirebaseIdpLogin() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirebaseIosAuthTransport(
            responses = listOf(
                FirebaseIosAuthHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "localId": "user-123",
                          "email": "alex@example.com",
                          "idToken": "firebase-id-token",
                          "refreshToken": "firebase-refresh-token",
                          "expiresIn": "3600"
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirebaseIosAuthRepository(
            apiKey = "firebase-api-key",
            transport = transport,
            nowProvider = { Instant.parse("2026-04-14T10:00:00Z") },
        )

        val result = repository.loginWithGoogle("google-id-token")

        assertTrue(result.isSuccess)
        val request = transport.requests.single()
        assertEquals(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=firebase-api-key",
            request.url,
        )
        assertTrue(request.body.contains(""""requestUri":"http://localhost""""))
        assertTrue(request.body.contains("id_token=google-id-token"))
        assertTrue(request.body.contains("providerId=google.com"))
        assertEquals("user-123", repository.currentUserId)
    }

    @Test
    fun loginWithGoogle_accessTokenFallbackUsesAccessTokenField() =
        kotlinx.coroutines.test.runTest {
            val transport = RecordingFirebaseIosAuthTransport(
                responses = listOf(
                    FirebaseIosAuthHttpResponse(
                        statusCode = 200,
                        body = """
                        {
                          "localId": "user-123",
                          "idToken": "firebase-id-token",
                          "refreshToken": "firebase-refresh-token",
                          "expiresIn": "3600"
                        }
                    """.trimIndent(),
                    ),
                ),
            )
            val repository = FirebaseIosAuthRepository(
                apiKey = "firebase-api-key",
                transport = transport,
                nowProvider = { Instant.parse("2026-04-14T10:00:00Z") },
            )

            val result = repository.loginWithGoogle(
                encodeIosGoogleAccessToken("google-access-token"),
            )

            assertTrue(result.isSuccess)
            val request = transport.requests.single()
            assertTrue(request.body.contains("access_token=google-access-token"))
            assertTrue(!request.body.contains("id_token=google-access-token"))
        }

    private class RecordingFirebaseIosAuthTransport(
        responses: List<FirebaseIosAuthHttpResponse>,
    ) : FirebaseIosAuthTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<FirebaseIosAuthRequest>()

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
        ): FirebaseIosAuthHttpResponse {
            requests += FirebaseIosAuthRequest(
                url = url,
                body = body,
                contentType = contentType,
            )
            return pendingResponses.removeFirst()
        }
    }

    private data class FirebaseIosAuthRequest(
        val url: String,
        val body: String,
        val contentType: String,
    )
}
