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
    fun getLists_runsCollectionGroupQueryAndMapsOwnerId() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        [
                          {
                            "document": {
                              "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1",
                              "fields": {
                                "name": { "stringValue": "Trabajo" },
                                "contributors": {
                                  "arrayValue": {
                                    "values": [
                                      { "stringValue": "user-123" }
                                    ]
                                  }
                                },
                                "creator": { "stringValue": "user-123" },
                                "date": { "timestampValue": "2026-04-12T10:00:00Z" },
                                "ordered": { "booleanValue": true }
                              }
                            }
                          },
                          {
                            "document": {
                              "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-2",
                              "fields": {
                                "name": { "stringValue": "Compartida" },
                                "contributors": {
                                  "arrayValue": {
                                    "values": [
                                      { "stringValue": "user-999" },
                                      { "stringValue": "user-123" }
                                    ]
                                  }
                                },
                                "creator": { "stringValue": "user-999" },
                                "date": { "timestampValue": "2026-04-12T12:00:00Z" },
                                "ordered": { "booleanValue": false }
                              }
                            }
                          }
                        ]
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
        assertEquals(2, lists.size)
        assertEquals("user-123", lists[0].ownerId)
        assertEquals(false, lists[0].isShared)
        assertEquals("user-999", lists[1].ownerId)
        assertEquals(true, lists[1].isShared)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents:runQuery",
            transport.requests.single().url,
        )
        assertEquals("POST", transport.requests.single().method)
        assertEquals("Bearer desktop-token", transport.requests.single().headers["Authorization"])
        assertTrue(transport.requests.single().body!!.contains(""""structuredQuery""""))
        assertTrue(transport.requests.single().body!!.contains(""""collectionId":"noteslist""""))
        assertTrue(transport.requests.single().body!!.contains(""""allDescendants":true"""))
        assertTrue(transport.requests.single().body!!.contains(""""op":"ARRAY_CONTAINS""""))
        assertTrue(transport.requests.single().body!!.contains(""""fieldPath":"contributors""""))
        assertTrue(transport.requests.single().body!!.contains(""""stringValue":"user-123""""))
    }

    @Test
    fun shareList_patchesContributorsWithPrecondition() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1",
                          "fields": {
                            "contributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-123" }
                                ]
                              }
                            }
                          },
                          "updateTime": "2026-04-15T10:00:00Z"
                        }
                    """.trimIndent(),
                ),
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1",
                          "fields": {
                            "contributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-123" },
                                  { "stringValue": "friend-1" }
                                ]
                              }
                            }
                          }
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

        val result = repository.shareList("list-1", "friend-1")

        assertTrue(result.isSuccess)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("PATCH", transport.requests[1].method)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1",
            transport.requests[0].url,
        )
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1?updateMask.fieldPaths=contributors&currentDocument.updateTime=2026-04-15T10%3A00%3A00Z",
            transport.requests[1].url,
        )
        assertEquals("Bearer desktop-token", transport.requests[1].headers["Authorization"])
        assertTrue(transport.requests[1].body!!.contains(""""contributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"friend-1""""))
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
        override suspend fun login(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signup(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun loginWithGoogle(idToken: String?): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(): Result<Boolean> =
            Result.success(currentUserId != null)
    }
}
