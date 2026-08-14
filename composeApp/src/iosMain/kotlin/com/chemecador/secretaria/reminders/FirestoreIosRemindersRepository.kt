package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.firestore.FirestoreIosDocument
import com.chemecador.secretaria.firestore.FirestoreIosDocumentPatch
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.arrayContainsFilter
import com.chemecador.secretaria.firestore.collectionQuery
import com.chemecador.secretaria.firestore.firestoreArray
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInt
import com.chemecador.secretaria.firestore.firestoreInteger
import com.chemecador.secretaria.firestore.firestoreNull
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreStringList
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreIosRemindersRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseIosFirestoreRestApi,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : RemindersRepository {

    /**
     * Los propios se leen por ruta y los ajenos por consulta de grupo de colecciones: asi los
     * recordatorios anteriores al reparto, que no tienen `contributors`, siguen apareciendo sin
     * necesidad de migrar nada.
     */
    override suspend fun getReminders(): Result<List<Reminder>> =
        runCatching {
            val userId = requireUserId()
            val own = firestore.listDocuments(
                collectionPath = remindersCollectionPath(userId),
                orderBy = "order",
            )
            // Si las reglas o el indice de grupo todavia no estan desplegados, la consulta de
            // compartidos falla: los recordatorios propios tienen que seguir viendose igual.
            val shared = runCatching {
                firestore.runQuery(
                    structuredQuery = collectionQuery(
                        collectionId = REMINDERS,
                        arrayContainsFilter(CONTRIBUTORS, firestoreString(userId)),
                        allDescendants = true,
                    ),
                )
            }.getOrDefault(emptyList())
            (own + shared)
                .map { document -> document.toReminder(userId) }
                .distinctBy(Reminder::key)
        }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> =
        runCatching {
            val userId = requireUserId()
            val nextOrder = nextPendingOrder(userId)
            firestore.createDocument(
                parentPath = userDocumentPath(userId),
                collectionId = REMINDERS,
                fields = buildJsonObject {
                    put("text", firestoreString(text))
                    put("dueDate", due.dueDateField())
                    put("dueTime", due.dueTimeField())
                    put("completed", firestoreBoolean(false))
                    put("completedAt", firestoreNull())
                    put("order", firestoreInteger(nextOrder))
                    put("date", firestoreTimestamp(nowProvider()))
                    put(CONTRIBUTORS, firestoreArray(firestoreString(userId)))
                },
            ).toReminder(userId)
        }

    override suspend fun updateReminder(
        key: ReminderKey,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> =
        runCatching {
            firestore.patchDocument(
                documentPath = reminderDocumentPath(key),
                fields = buildJsonObject {
                    put("text", firestoreString(text))
                    put("dueDate", due.dueDateField())
                    put("dueTime", due.dueTimeField())
                },
                updateMask = listOf("text", "dueDate", "dueTime"),
            ).toReminder(requireUserId())
        }

    override suspend fun setReminderCompleted(
        key: ReminderKey,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> =
        runCatching {
            firestore.patchDocument(
                documentPath = reminderDocumentPath(key),
                fields = buildJsonObject {
                    put("completed", firestoreBoolean(completed))
                    put("completedAt", completedAt?.let(::firestoreTimestamp) ?: firestoreNull())
                    put("order", firestoreInteger(order))
                },
                updateMask = listOf("completed", "completedAt", "order"),
            )
            Unit
        }

    override suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit> =
        runCatching {
            firestore.commitPatches(
                reminderKeysInOrder.mapIndexed { index, key ->
                    FirestoreIosDocumentPatch(
                        documentPath = reminderDocumentPath(key),
                        fields = buildJsonObject {
                            put("order", firestoreInteger(index))
                        },
                        updateMask = listOf("order"),
                    )
                },
            )
        }

    override suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit> =
        runCatching {
            firestore.commitDeletes(reminderKeys.map(::reminderDocumentPath))
        }

    override suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit> =
        updateContributors(reminderId) { contributors -> contributors + friendUserId }

    override suspend fun unshareReminder(reminderId: String, friendUserId: String): Result<Unit> =
        updateContributors(reminderId) { contributors ->
            contributors.filterNot { contributorId -> contributorId == friendUserId }
        }

    override suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            if (key.ownerId == userId) error("Owner cannot leave own reminder")
            val document = firestore.getDocumentOrNull(reminderDocumentPath(key))
                ?: error("Reminder not found")
            val contributors = document.fields.contributors(key.ownerId)
            if (userId !in contributors) error("Reminder not found")
            firestore.patchDocument(
                documentPath = reminderDocumentPath(key),
                fields = buildJsonObject {
                    put(
                        CONTRIBUTORS,
                        contributorsField(
                            key.ownerId,
                            contributors.filterNot { contributorId -> contributorId == userId },
                        ),
                    )
                },
                updateMask = listOf(CONTRIBUTORS),
            )
            Unit
        }

    private suspend fun updateContributors(
        reminderId: String,
        update: (List<String>) -> List<String>,
    ): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val key = ReminderKey(userId, reminderId)
            val document = firestore.getDocumentOrNull(reminderDocumentPath(key))
                ?: error("Reminder not found")
            firestore.patchDocument(
                documentPath = reminderDocumentPath(key),
                fields = buildJsonObject {
                    put(
                        CONTRIBUTORS,
                        contributorsField(userId, update(document.fields.contributors(userId))),
                    )
                },
                updateMask = listOf(CONTRIBUTORS),
            )
            Unit
        }

    private suspend fun nextPendingOrder(userId: String): Int =
        firestore.listDocuments(collectionPath = remindersCollectionPath(userId))
            .filterNot { it.fields.firestoreBoolean("completed") ?: false }
            .maxOfOrNull { it.fields.firestoreInt("order") ?: 0 }
            ?.plus(1)
            ?: 0

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private fun userDocumentPath(ownerId: String): String =
        "$USERS/$ownerId"

    private fun remindersCollectionPath(ownerId: String): String =
        "${userDocumentPath(ownerId)}/$REMINDERS"

    private fun reminderDocumentPath(key: ReminderKey): String =
        "${remindersCollectionPath(key.ownerId)}/${key.reminderId}"

    private companion object {
        const val USERS = "users"
        const val REMINDERS = "reminders"
        const val CONTRIBUTORS = "contributors"
    }
}

private fun contributorsField(ownerId: String, contributors: List<String>): JsonObject =
    firestoreArray(
        *effectiveReminderContributors(ownerId, contributors)
            .map(::firestoreString)
            .toTypedArray(),
    )

private fun JsonObject.contributors(ownerId: String): List<String> =
    effectiveReminderContributors(ownerId, firestoreStringList("contributors"))

private fun ReminderDue?.dueDateField() =
    this?.let { firestoreString(it.date.toString()) } ?: firestoreNull()

private fun ReminderDue?.dueTimeField() =
    this?.time?.let { firestoreString(it.toString()) } ?: firestoreNull()

private fun FirestoreIosDocument.toReminder(currentUserId: String): Reminder {
    val dueDate = fields.firestoreString("dueDate")?.let(LocalDate::parse)
    val ownerId = reminderOwnerIdFromDocumentName(name)
    val contributors = fields.contributors(ownerId)
    return Reminder(
        id = id,
        ownerId = ownerId,
        text = fields.firestoreString("text").orEmpty(),
        createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
        due = dueDate?.let { date ->
            ReminderDue(date, fields.firestoreString("dueTime")?.let(LocalTime::parse))
        },
        completed = fields.firestoreBoolean("completed") ?: false,
        completedAt = fields.firestoreInstant("completedAt"),
        order = fields.firestoreInt("order") ?: 0,
        contributors = contributors,
        isShared = ownerId != currentUserId || contributors.size > 1,
    )
}
