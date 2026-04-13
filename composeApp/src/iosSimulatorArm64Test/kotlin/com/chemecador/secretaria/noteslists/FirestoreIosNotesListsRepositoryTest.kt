package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreIosNotesListsRepositoryTest {

    @Test
    fun getLists_readsFromFirestoreAndMapsDocuments() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
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
        val repository = FirestoreIosNotesListsRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseIosFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("ios-token"),
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
            "Bearer ios-token",
            transport.requests.single().headers["Authorization"],
        )
    }

    @Test
    fun deleteList_commitsDeletesForNotesAndListDocument() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "documents": [
                            {
                              "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1/notes/note-1",
                              "fields": {}
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = "{}",
                ),
            ),
        )
        val repository = FirestoreIosNotesListsRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseIosFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("ios-token"),
                transport = transport,
            ),
        )

        val result = repository.deleteList("list-1")

        assertTrue(result.isSuccess)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents:commit",
            transport.requests[1].url,
        )
        assertTrue(
            transport.requests[1].body!!.contains(
                """"delete":"projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1/notes/note-1"""",
            ),
        )
        assertTrue(
            transport.requests[1].body!!.contains(
                """"delete":"projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1"""",
            ),
        )
    }

    private class RecordingFirestoreTransport(
        responses: List<FirebaseIosFirestoreHttpResponse>,
    ) : FirebaseIosFirestoreTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<FirebaseIosFirestoreRequest>()

        override suspend fun execute(request: FirebaseIosFirestoreRequest): FirebaseIosFirestoreHttpResponse {
            requests += request
            return pendingResponses.removeFirst()
        }
    }

    private class StaticTokenProvider(
        private val token: String,
    ) : FirebaseIosIdTokenProvider {
        override suspend fun getFreshIdToken(): String = token
    }

    private class LoggedInAuthRepository(
        override val currentUserId: String?,
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun signup(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun loginWithGoogle(): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
    }
}
