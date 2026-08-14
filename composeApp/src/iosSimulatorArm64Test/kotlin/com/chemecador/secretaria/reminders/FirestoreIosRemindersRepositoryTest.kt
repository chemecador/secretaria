package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FirestoreIosRemindersRepositoryTest {

    @Test
    fun createReminder_writesFloatingDueDateAndTime() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(statusCode = 200, body = """{"documents":[]}"""),
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                          "fields": {
                            "text": { "stringValue": "Llamar al fontanero" },
                            "dueDate": { "stringValue": "2026-08-20" },
                            "dueTime": { "stringValue": "09:30" },
                            "completed": { "booleanValue": false },
                            "completedAt": { "nullValue": "NULL_VALUE" },
                            "order": { "integerValue": "0" },
                            "date": { "timestampValue": "2026-08-13T10:00:00Z" }
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = buildRepository(transport)

        val result = repository.createReminder(
            text = "Llamar al fontanero",
            due = ReminderDue(LocalDate.parse("2026-08-20"), LocalTime.parse("09:30")),
        )

        assertTrue(result.isSuccess)
        val reminder = result.getOrThrow()
        assertEquals("reminder-1", reminder.id)
        assertEquals(LocalDate.parse("2026-08-20"), reminder.due?.date)
        assertEquals(LocalTime.parse("09:30"), reminder.due?.time)

        assertEquals("$REMINDERS_URL", transport.requests[0].url)
        assertEquals("$REMINDERS_URL", transport.requests[1].url)
        assertEquals("Bearer ios-token", transport.requests[1].headers["Authorization"])
        val body = transport.requests[1].body!!
        assertTrue(body.contains(""""dueDate":{"stringValue":"2026-08-20"}"""))
        assertTrue(body.contains(""""dueTime":{"stringValue":"09:30"}"""))
        assertTrue(body.contains(""""completedAt":{"nullValue":"NULL_VALUE"}"""))
        assertTrue(body.contains(""""date":{"timestampValue":"2026-08-13T10:00:00Z"}"""))
        assertTrue(body.contains(""""contributors":{"arrayValue":{"values":[{"stringValue":"user-123"}]}}"""))
    }

    @Test
    fun createReminder_withoutDueWritesNullsAndTakesNextPendingOrder() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "documents": [
                            {
                              "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                              "fields": {
                                "text": { "stringValue": "Pendiente" },
                                "completed": { "booleanValue": false },
                                "order": { "integerValue": "4" }
                              }
                            },
                            {
                              "name": "$DOCUMENTS/users/user-123/reminders/reminder-2",
                              "fields": {
                                "text": { "stringValue": "Ya hecho" },
                                "completed": { "booleanValue": true },
                                "order": { "integerValue": "9" }
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "$DOCUMENTS/users/user-123/reminders/reminder-3",
                          "fields": {
                            "text": { "stringValue": "Comprar pilas" },
                            "dueDate": { "nullValue": "NULL_VALUE" },
                            "dueTime": { "nullValue": "NULL_VALUE" },
                            "completed": { "booleanValue": false },
                            "order": { "integerValue": "5" }
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = buildRepository(transport)

        val result = repository.createReminder(text = "Comprar pilas", due = null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().due)
        val body = transport.requests[1].body!!
        // El completado con order 9 no cuenta: el siguiente hueco sale del maximo pendiente.
        assertTrue(body.contains(""""order":{"integerValue":"5"}"""))
        assertTrue(body.contains(""""dueDate":{"nullValue":"NULL_VALUE"}"""))
        assertTrue(body.contains(""""dueTime":{"nullValue":"NULL_VALUE"}"""))
    }

    @Test
    fun getReminders_mapsAllDayDueAndCompletedAt() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "documents": [
                            {
                              "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                              "fields": {
                                "text": { "stringValue": "Renovar el DNI" },
                                "dueDate": { "stringValue": "2026-08-22" },
                                "dueTime": { "nullValue": "NULL_VALUE" },
                                "completed": { "booleanValue": true },
                                "completedAt": { "timestampValue": "2026-08-13T10:00:00Z" },
                                "order": { "integerValue": "2" }
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
                FirebaseIosFirestoreHttpResponse(statusCode = 200, body = """[]"""),
            ),
        )
        val repository = buildRepository(transport)

        val reminders = repository.getReminders().getOrThrow()

        val reminder = reminders.single()
        assertEquals("user-123", reminder.ownerId)
        assertEquals(LocalDate.parse("2026-08-22"), reminder.due?.date)
        assertNull(reminder.due?.time)
        assertTrue(reminder.completed)
        assertEquals(NOW, reminder.completedAt)
        assertEquals(2, reminder.order)
        assertTrue(transport.requests[0].url.startsWith("$REMINDERS_URL?"))
        assertTrue(transport.requests[0].url.contains("orderBy=order"))
        assertEquals("$DOCUMENTS_URL:runQuery", transport.requests[1].url)
        assertTrue(transport.requests[1].body!!.contains(""""allDescendants":true"""))
        assertTrue(transport.requests[1].body!!.contains(""""op":"ARRAY_CONTAINS""""))
    }

    @Test
    fun getReminders_addsRemindersSharedByFriendsWithoutDuplicatingOwnOnes() =
        kotlinx.coroutines.test.runTest {
            val transport = RecordingFirestoreTransport(
                responses = listOf(
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                            {
                              "documents": [
                                {
                                  "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                                  "fields": {
                                    "text": { "stringValue": "Propio compartido" },
                                    "contributors": {
                                      "arrayValue": {
                                        "values": [
                                          { "stringValue": "user-123" },
                                          { "stringValue": "friend-1" }
                                        ]
                                      }
                                    },
                                    "order": { "integerValue": "0" }
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                    ),
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                            [
                              {
                                "document": {
                                  "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                                  "fields": {
                                    "text": { "stringValue": "Propio compartido" },
                                    "order": { "integerValue": "0" }
                                  }
                                }
                              },
                              {
                                "document": {
                                  "name": "$DOCUMENTS/users/friend-1/reminders/reminder-9",
                                  "fields": {
                                    "text": { "stringValue": "Ajeno" },
                                    "contributors": {
                                      "arrayValue": {
                                        "values": [
                                          { "stringValue": "friend-1" },
                                          { "stringValue": "user-123" }
                                        ]
                                      }
                                    },
                                    "order": { "integerValue": "1" }
                                  }
                                }
                              }
                            ]
                        """.trimIndent(),
                    ),
                ),
            )
            val repository = buildRepository(transport)

            val reminders = repository.getReminders().getOrThrow()

            assertEquals(listOf("reminder-1", "reminder-9"), reminders.map(Reminder::id))
            assertEquals(listOf("user-123", "friend-1"), reminders.map(Reminder::ownerId))
            assertTrue(reminders.all(Reminder::isShared))
            assertEquals(listOf("friend-1"), reminders.first().sharedWithUserIds)
        }

    /** Sin reglas ni indice de grupo desplegados lo propio tiene que seguir cargando. */
    @Test
    fun getReminders_keepsOwnRemindersWhenTheSharedQueryIsRejected() =
        kotlinx.coroutines.test.runTest {
            val transport = RecordingFirestoreTransport(
                responses = listOf(
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                            {
                              "documents": [
                                {
                                  "name": "${'$'}DOCUMENTS/users/user-123/reminders/reminder-1",
                                  "fields": { "text": { "stringValue": "Propio" } }
                                }
                              ]
                            }
                        """.trimIndent(),
                    ),
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 403,
                        body = """{"error":{"message":"Missing or insufficient permissions."}}""",
                    ),
                ),
            )
            val repository = buildRepository(transport)

            val reminders = repository.getReminders().getOrThrow()

            assertEquals(listOf("reminder-1"), reminders.map(Reminder::id))
        }

    @Test
    fun setReminderCompleted_patchesCompletionFieldsWithTheGivenClock() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                          "fields": { "text": { "stringValue": "Recoger paquete" } }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = buildRepository(transport)

        val result = repository.setReminderCompleted(
            key = ReminderKey("user-123", "reminder-1"),
            completed = true,
            completedAt = NOW,
            order = 7,
        )

        assertTrue(result.isSuccess)
        val request = transport.requests.single()
        assertEquals("PATCH", request.method)
        assertTrue(request.url.startsWith("$REMINDERS_URL/reminder-1?"))
        assertTrue(request.url.contains("updateMask.fieldPaths=completed"))
        assertTrue(request.url.contains("updateMask.fieldPaths=completedAt"))
        assertTrue(request.url.contains("updateMask.fieldPaths=order"))
        val body = request.body!!
        assertTrue(body.contains(""""completed":{"booleanValue":true}"""))
        assertTrue(body.contains(""""completedAt":{"timestampValue":"2026-08-13T10:00:00Z"}"""))
        assertTrue(body.contains(""""order":{"integerValue":"7"}"""))
    }

    @Test
    fun setReminderCompleted_restoringClearsCompletedAt() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """{"name":"$DOCUMENTS/users/user-123/reminders/reminder-1","fields":{}}""",
                ),
            ),
        )
        val repository = buildRepository(transport)

        repository.setReminderCompleted(
            key = ReminderKey("user-123", "reminder-1"),
            completed = false,
            completedAt = null,
            order = 3,
        ).getOrThrow()

        assertTrue(transport.requests.single().body!!.contains(""""completedAt":{"nullValue":"NULL_VALUE"}"""))
    }

    @Test
    fun deleteReminders_commitsEveryDeleteInASingleRequest() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(FirebaseIosFirestoreHttpResponse(statusCode = 200, body = """{}""")),
        )
        val repository = buildRepository(transport)

        val result = repository.deleteReminders(
            listOf(
                ReminderKey("user-123", "reminder-1"),
                ReminderKey("user-123", "reminder-2"),
            ),
        )

        assertTrue(result.isSuccess)
        val request = transport.requests.single()
        assertEquals("$DOCUMENTS_URL:commit", request.url)
        val body = request.body!!
        assertTrue(body.contains(""""delete":"$DOCUMENTS/users/user-123/reminders/reminder-1""""))
        assertTrue(body.contains(""""delete":"$DOCUMENTS/users/user-123/reminders/reminder-2""""))
    }

    @Test
    fun shareReminder_patchesContributorsKeepingTheOwner() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "$DOCUMENTS/users/user-123/reminders/reminder-1",
                          "fields": {
                            "text": { "stringValue": "Comprar pan" },
                            "contributors": {
                              "arrayValue": { "values": [ { "stringValue": "user-123" } ] }
                            }
                          }
                        }
                    """.trimIndent(),
                ),
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """{"name":"$DOCUMENTS/users/user-123/reminders/reminder-1","fields":{}}""",
                ),
            ),
        )
        val repository = buildRepository(transport)

        val result = repository.shareReminder(reminderId = "reminder-1", friendUserId = "friend-1")

        assertTrue(result.isSuccess)
        val patch = transport.requests[1]
        assertEquals("PATCH", patch.method)
        assertTrue(patch.url.contains("updateMask.fieldPaths=contributors"))
        assertTrue(
            patch.body!!.contains(
                """"contributors":{"arrayValue":{"values":[{"stringValue":"user-123"},{"stringValue":"friend-1"}]}}""",
            ),
        )
    }

    @Test
    fun leaveSharedReminder_removesOnlyTheCurrentUserFromTheOwnerDocument() =
        kotlinx.coroutines.test.runTest {
            val transport = RecordingFirestoreTransport(
                responses = listOf(
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 200,
                        body = """
                            {
                              "name": "$DOCUMENTS/users/friend-1/reminders/reminder-9",
                              "fields": {
                                "contributors": {
                                  "arrayValue": {
                                    "values": [
                                      { "stringValue": "friend-1" },
                                      { "stringValue": "user-123" },
                                      { "stringValue": "friend-2" }
                                    ]
                                  }
                                }
                              }
                            }
                        """.trimIndent(),
                    ),
                    FirebaseIosFirestoreHttpResponse(
                        statusCode = 200,
                        body = """{"name":"$DOCUMENTS/users/friend-1/reminders/reminder-9","fields":{}}""",
                    ),
                ),
            )
            val repository = buildRepository(transport)

            val result = repository.leaveSharedReminder(ReminderKey("friend-1", "reminder-9"))

            assertTrue(result.isSuccess)
            val patch = transport.requests[1]
            assertTrue(patch.url.startsWith("$DOCUMENTS_URL/users/friend-1/reminders/reminder-9?"))
            assertTrue(
                patch.body!!.contains(
                    """"contributors":{"arrayValue":{"values":[{"stringValue":"friend-1"},{"stringValue":"friend-2"}]}}""",
                ),
            )
        }

    @Test
    fun leaveSharedReminder_isRejectedForOwnReminders() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(responses = emptyList())
        val repository = buildRepository(transport)

        val result = repository.leaveSharedReminder(ReminderKey("user-123", "reminder-1"))

        assertTrue(result.isFailure)
        assertTrue(transport.requests.isEmpty())
    }

    private fun buildRepository(
        transport: RecordingFirestoreTransport,
    ): FirestoreIosRemindersRepository = FirestoreIosRemindersRepository(
        authRepository = LoggedInAuthRepository("user-123"),
        firestore = FirebaseIosFirestoreRestApi(
            projectId = "project-id",
            tokenProvider = StaticTokenProvider("ios-token"),
            transport = transport,
        ),
        nowProvider = { NOW },
    )

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

    private companion object {
        const val DOCUMENTS = "projects/project-id/databases/(default)/documents"
        const val DOCUMENTS_URL = "https://firestore.googleapis.com/v1/$DOCUMENTS"
        const val REMINDERS_URL = "$DOCUMENTS_URL/users/user-123/reminders"
        val NOW: Instant = Instant.parse("2026-08-13T10:00:00Z")
    }
}
