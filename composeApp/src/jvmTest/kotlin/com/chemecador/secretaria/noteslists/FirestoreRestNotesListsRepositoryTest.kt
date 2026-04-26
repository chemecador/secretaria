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
import kotlin.time.Instant

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
                                "ordered": { "booleanValue": false },
                                "groupId": { "stringValue": "group-1" },
                                "groupOwnerId": { "stringValue": "user-123" }
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
        assertEquals(listOf("user-123"), lists[0].contributors)
        assertTrue(lists[0].archivedBy.isEmpty())
        assertTrue(lists[0].archivedAtBy.isEmpty())
        assertEquals("user-999", lists[1].ownerId)
        assertEquals(true, lists[1].isShared)
        assertEquals("group-1", lists[1].groupId)
        assertEquals("user-123", lists[1].groupOwnerId)
        assertEquals(listOf("user-999", "user-123"), lists[1].contributors)
        assertTrue(lists[1].archivedBy.isEmpty())
        assertTrue(lists[1].archivedAtBy.isEmpty())
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
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1?updateMask.fieldPaths=directContributors&updateMask.fieldPaths=contributors&currentDocument.updateTime=2026-04-15T10%3A00%3A00Z",
            transport.requests[1].url,
        )
        assertEquals("Bearer desktop-token", transport.requests[1].headers["Authorization"])
        assertTrue(transport.requests[1].body!!.contains(""""directContributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""contributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"friend-1""""))
    }

    @Test
    fun unshareList_removesContributorWithPrecondition() = kotlinx.coroutines.test.runTest {
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
                                  { "stringValue": "user-123" },
                                  { "stringValue": "friend-1" }
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
                                  { "stringValue": "user-123" }
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

        val result = repository.unshareList("list-1", "friend-1")

        assertTrue(result.isSuccess)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("PATCH", transport.requests[1].method)
        assertTrue(transport.requests[1].url.contains("updateMask.fieldPaths=directContributors"))
        assertTrue(transport.requests[1].url.contains("updateMask.fieldPaths=contributors"))
        assertTrue(transport.requests[1].body!!.contains(""""directContributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""contributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(!transport.requests[1].body!!.contains(""""stringValue":"friend-1""""))
    }

    @Test
    fun leaveSharedList_patchesSharedListUnderRealOwner() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "contributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-999" },
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
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "contributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-999" }
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

        val result = repository.leaveSharedList("user-999", "list-1")

        assertTrue(result.isSuccess)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("PATCH", transport.requests[1].method)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
            transport.requests[0].url,
        )
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1?updateMask.fieldPaths=directContributors&updateMask.fieldPaths=contributors&currentDocument.updateTime=2026-04-15T10%3A00%3A00Z",
            transport.requests[1].url,
        )
        assertTrue(transport.requests[1].body!!.contains(""""directContributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""contributors""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"user-999""""))
        assertTrue(!transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
    }

    @Test
    fun setListGroup_patchesSharedListUnderRealOwner() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "contributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-999" },
                                  { "stringValue": "user-123" }
                                ]
                              }
                            },
                            "directContributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-999" },
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
                          "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/group-1",
                          "fields": {
                            "isGroup": { "booleanValue": true },
                            "directContributors": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "user-123" },
                                  { "stringValue": "friend-1" }
                                ]
                              }
                            }
                          },
                          "updateTime": "2026-04-15T11:00:00Z"
                        }
                    """.trimIndent(),
                ),
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = "[]",
                ),
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {}
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

        val result = repository.setListGroup(
            listOwnerId = "user-999",
            listId = "list-1",
            groupOwnerId = "user-123",
            groupId = "group-1",
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
            transport.requests[0].url,
        )
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/group-1",
            transport.requests[1].url,
        )
        assertEquals("POST", transport.requests[2].method)
        assertEquals("PATCH", transport.requests[3].method)
        assertTrue(transport.requests[3].url.contains("/users/user-999/noteslist/list-1?"))
        assertTrue(transport.requests[3].url.contains("updateMask.fieldPaths=groupOwnerId"))
        assertTrue(transport.requests[3].body!!.contains(""""groupOwnerId""""))
        assertTrue(transport.requests[3].body!!.contains(""""stringValue":"user-999""""))
        assertTrue(transport.requests[3].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(transport.requests[3].body!!.contains(""""stringValue":"friend-1""""))
    }

    @Test
    fun reorderGroupedLists_commitsOrderToEachListOwnerPath() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = "{}",
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

        val result = repository.reorderGroupedLists(
            groupOwnerId = "user-123",
            groupId = "group-1",
            listKeysInOrder = listOf(
                NotesListKey("user-999", "list-b"),
                NotesListKey("user-123", "list-a"),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals("POST", transport.requests.single().method)
        assertTrue(transport.requests.single().body!!.contains("/documents/users/user-999/noteslist/list-b"))
        assertTrue(transport.requests.single().body!!.contains("/documents/users/user-123/noteslist/list-a"))
        assertTrue(transport.requests.single().body!!.contains(""""groupOrder""""))
    }

    @Test
    fun setListArchived_addsCurrentUserWithPrecondition() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "archivedBy": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "other-user" }
                                ]
                              }
                            },
                            "archivedAtBy": {
                              "mapValue": {
                                "fields": {
                                  "other-user": { "timestampValue": "2026-04-14T09:00:00Z" }
                                }
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
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "archivedBy": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "other-user" },
                                  { "stringValue": "user-123" }
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
            nowProvider = { Instant.parse("2026-04-20T10:00:00Z") },
        )

        val result = repository.setListArchived("user-999", "list-1", archived = true)

        assertTrue(result.isSuccess)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("PATCH", transport.requests[1].method)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
            transport.requests[0].url,
        )
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1?updateMask.fieldPaths=archivedBy&updateMask.fieldPaths=archivedAtBy&currentDocument.updateTime=2026-04-15T10%3A00%3A00Z",
            transport.requests[1].url,
        )
        assertTrue(transport.requests[1].body!!.contains(""""archivedBy""""))
        assertTrue(transport.requests[1].body!!.contains(""""archivedAtBy""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"other-user""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(transport.requests[1].body!!.contains(""""timestampValue":"2026-04-14T09:00:00Z""""))
        assertTrue(transport.requests[1].body!!.contains(""""timestampValue":"2026-04-20T10:00:00Z""""))
    }

    @Test
    fun setListArchived_removesOnlyCurrentUserWithPrecondition() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "archivedBy": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "other-user" },
                                  { "stringValue": "user-123" }
                                ]
                              }
                            },
                            "archivedAtBy": {
                              "mapValue": {
                                "fields": {
                                  "other-user": { "timestampValue": "2026-04-14T09:00:00Z" },
                                  "user-123": { "timestampValue": "2026-04-13T09:00:00Z" }
                                }
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
                          "name": "projects/project-id/databases/(default)/documents/users/user-999/noteslist/list-1",
                          "fields": {
                            "archivedBy": {
                              "arrayValue": {
                                "values": [
                                  { "stringValue": "other-user" }
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

        val result = repository.setListArchived("user-999", "list-1", archived = false)

        assertTrue(result.isSuccess)
        assertEquals("PATCH", transport.requests[1].method)
        assertTrue(transport.requests[1].body!!.contains(""""archivedBy""""))
        assertTrue(transport.requests[1].body!!.contains(""""archivedAtBy""""))
        assertTrue(transport.requests[1].body!!.contains(""""stringValue":"other-user""""))
        assertTrue(!transport.requests[1].body!!.contains(""""stringValue":"user-123""""))
        assertTrue(transport.requests[1].body!!.contains(""""timestampValue":"2026-04-14T09:00:00Z""""))
        assertTrue(!transport.requests[1].body!!.contains(""""user-123""""))
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
