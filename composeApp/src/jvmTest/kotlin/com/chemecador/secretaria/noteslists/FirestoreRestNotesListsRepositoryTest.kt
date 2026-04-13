package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIdTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreRestNotesListsRepositoryTest {

    @Test
    fun getLists_readsFromFirestoreAndMapsDocuments() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "documents": [
                            {
                              "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1",
                              "fields": {
                                "name": { "stringValue": "Trabajo" },
                                "creator": { "stringValue": "user-123" },
                                "date": { "timestampValue": "2026-04-12T10:00:00Z" },
                                "ordered": { "booleanValue": true }
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirestoreRestNotesListsRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("desktop-token"),
                transport = transport,
            ),
        )

        val result = repository.getLists()

        assertTrue(result.isSuccess)
        val lists = result.getOrThrow()
        assertEquals(1, lists.size)
        assertEquals("list-1", lists.single().id)
        assertEquals("Trabajo", lists.single().name)
        assertTrue(lists.single().isOrdered)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist",
            transport.requests.single().url,
        )
        assertEquals(
            "Bearer desktop-token",
            transport.requests.single().headers["Authorization"],
        )
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
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signup(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun loginWithGoogle(): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
    }
}
