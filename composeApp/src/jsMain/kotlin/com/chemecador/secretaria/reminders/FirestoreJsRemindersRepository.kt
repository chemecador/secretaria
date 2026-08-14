package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.firestore.FirestoreJsDocument
import com.chemecador.secretaria.firestore.FirestoreJsDocumentPatch
import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInt
import com.chemecador.secretaria.firestore.firestoreInteger
import com.chemecador.secretaria.firestore.firestoreNull
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.Date
import kotlin.time.Instant

internal class FirestoreJsRemindersRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseJsFirestoreRestApi,
    private val nowProvider: () -> Instant = { Instant.fromEpochMilliseconds(Date.now().toLong()) },
) : RemindersRepository {

    override suspend fun getReminders(): Result<List<Reminder>> =
        runCatching {
            firestore.listDocuments(
                collectionPath = remindersCollectionPath(),
                orderBy = "order",
            ).map(FirestoreJsDocument::toReminder)
        }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> =
        runCatching {
            val nextOrder = nextPendingOrder()
            firestore.createDocument(
                parentPath = userDocumentPath(),
                collectionId = REMINDERS,
                fields = buildJsonObject {
                    put("text", firestoreString(text))
                    put("dueDate", due.dueDateField())
                    put("dueTime", due.dueTimeField())
                    put("completed", firestoreBoolean(false))
                    put("completedAt", firestoreNull())
                    put("order", firestoreInteger(nextOrder))
                    put("date", firestoreTimestamp(nowProvider()))
                },
            ).toReminder()
        }

    override suspend fun updateReminder(
        reminderId: String,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> =
        runCatching {
            firestore.patchDocument(
                documentPath = reminderDocumentPath(reminderId),
                fields = buildJsonObject {
                    put("text", firestoreString(text))
                    put("dueDate", due.dueDateField())
                    put("dueTime", due.dueTimeField())
                },
                updateMask = listOf("text", "dueDate", "dueTime"),
            ).toReminder()
        }

    override suspend fun setReminderCompleted(
        reminderId: String,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> =
        runCatching {
            firestore.patchDocument(
                documentPath = reminderDocumentPath(reminderId),
                fields = buildJsonObject {
                    put("completed", firestoreBoolean(completed))
                    put("completedAt", completedAt?.let(::firestoreTimestamp) ?: firestoreNull())
                    put("order", firestoreInteger(order))
                },
                updateMask = listOf("completed", "completedAt", "order"),
            )
            Unit
        }

    override suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit> =
        runCatching {
            firestore.commitPatches(
                reminderIdsInOrder.mapIndexed { index, reminderId ->
                    FirestoreJsDocumentPatch(
                        documentPath = reminderDocumentPath(reminderId),
                        fields = buildJsonObject {
                            put("order", firestoreInteger(index))
                        },
                        updateMask = listOf("order"),
                    )
                },
            )
        }

    override suspend fun deleteReminders(reminderIds: List<String>): Result<Unit> =
        runCatching {
            firestore.commitDeletes(reminderIds.map(::reminderDocumentPath))
        }

    private suspend fun nextPendingOrder(): Int =
        firestore.listDocuments(collectionPath = remindersCollectionPath())
            .filterNot { it.fields.firestoreBoolean("completed") ?: false }
            .maxOfOrNull { it.fields.firestoreInt("order") ?: 0 }
            ?.plus(1)
            ?: 0

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private fun userDocumentPath(): String =
        "$USERS/${requireUserId()}"

    private fun remindersCollectionPath(): String =
        "${userDocumentPath()}/$REMINDERS"

    private fun reminderDocumentPath(reminderId: String): String =
        "${remindersCollectionPath()}/$reminderId"

    private companion object {
        const val USERS = "users"
        const val REMINDERS = "reminders"
    }
}

private fun ReminderDue?.dueDateField() =
    this?.let { firestoreString(it.date.toString()) } ?: firestoreNull()

private fun ReminderDue?.dueTimeField() =
    this?.time?.let { firestoreString(it.toString()) } ?: firestoreNull()

private fun FirestoreJsDocument.toReminder(): Reminder {
    val dueDate = fields.firestoreString("dueDate")?.let(LocalDate::parse)
    return Reminder(
        id = id,
        text = fields.firestoreString("text").orEmpty(),
        createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
        due = dueDate?.let { date ->
            ReminderDue(date, fields.firestoreString("dueTime")?.let(LocalTime::parse))
        },
        completed = fields.firestoreBoolean("completed") ?: false,
        completedAt = fields.firestoreInstant("completedAt"),
        order = fields.firestoreInt("order") ?: 0,
    )
}
