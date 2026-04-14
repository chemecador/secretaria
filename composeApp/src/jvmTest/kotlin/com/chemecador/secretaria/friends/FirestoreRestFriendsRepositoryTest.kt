package com.chemecador.secretaria.friends

import com.chemecador.secretaria.firestore.FirebaseFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIdTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreRestFriendsRepositoryTest {

    @Test
    fun getIncomingRequests_readsPendingRequestsFromRunQuery() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        [
                          {
                            "document": {
                              "name": "projects/project-id/databases/(default)/documents/friendships/request-1",
                              "fields": {
                                "senderId": { "stringValue": "friend-1" },
                                "senderName": { "stringValue": "Marina" },
                                "requestDate": { "timestampValue": "2026-04-13T10:00:00Z" },
                                "acceptanceDate": { "nullValue": null }
                              }
                            }
                          }
                        ]
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirestoreRestFriendsRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("desktop-token"),
                transport = transport,
            ),
        )

        val result = repository.getIncomingRequests()

        assertTrue(result.isSuccess)
        val requests = result.getOrThrow()
        assertEquals(1, requests.size)
        assertEquals("request-1", requests.single().id)
        assertEquals("Marina", requests.single().senderName)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents:runQuery",
            transport.requests.single().url,
        )
        assertEquals("Bearer desktop-token", transport.requests.single().headers["Authorization"])
        assertTrue(transport.requests.single().body!!.contains("structuredQuery"))
        assertTrue(transport.requests.single().body!!.contains("receiverId"))
    }

    @Test
    fun sendFriendRequest_createsFriendshipAfterValidationQueries() =
        kotlinx.coroutines.test.runTest {
            val transport = RecordingFirestoreTransport(
                responses = listOf(
                    FirebaseFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                        [
                          {
                            "document": {
                              "name": "projects/project-id/databases/(default)/documents/users/friend-9",
                              "fields": {
                                "usercode": { "stringValue": "26010602" }
                              }
                            }
                          }
                        ]
                    """.trimIndent(),
                    ),
                    FirebaseFirestoreHttpResponse(
                        statusCode = 200,
                        body = "[]",
                    ),
                    FirebaseFirestoreHttpResponse(
                        statusCode = 200,
                        body = "[]",
                    ),
                    FirebaseFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/friendships/request-1",
                          "fields": {}
                        }
                    """.trimIndent(),
                    ),
                ),
            )
            val repository = FirestoreRestFriendsRepository(
                authRepository = LoggedInAuthRepository(
                    currentUserId = "user-123",
                    currentUserEmail = "alex@example.com",
                ),
                firestore = FirebaseFirestoreRestApi(
                    projectId = "project-id",
                    tokenProvider = StaticTokenProvider("desktop-token"),
                    transport = transport,
                ),
                nowProvider = { kotlin.time.Instant.parse("2026-04-14T10:00:00Z") },
            )

            val result = repository.sendFriendRequest("26010602")

            assertTrue(result.isSuccess)
            assertEquals(4, transport.requests.size)
            assertTrue(transport.requests[0].body!!.contains("usercode"))
            assertTrue(transport.requests[1].body!!.contains("senderId"))
            assertTrue(transport.requests[2].body!!.contains("senderId"))
            assertEquals(
                "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/friendships",
                transport.requests[3].url,
            )
            assertTrue(transport.requests[3].body!!.contains("alex@example.com"))
            assertTrue(transport.requests[3].body!!.contains("26010602"))
        }

    private class RecordingFirestoreTransport(
        responses: List<FirebaseFirestoreHttpResponse>,
    ) : FirebaseFirestoreTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<FirebaseFirestoreRequest>()

        override suspend fun execute(request: FirebaseFirestoreRequest): FirebaseFirestoreHttpResponse {
            requests += request
            return pendingResponses.removeFirst()
        }
    }

    private class StaticTokenProvider(
        private val token: String,
    ) : FirebaseIdTokenProvider {
        override suspend fun getFreshIdToken(): String = token
    }

    private class LoggedInAuthRepository(
        override val currentUserId: String?,
        override val currentUserEmail: String? = null,
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun signup(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun loginWithGoogle(idToken: String?): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
    }
}
