package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant

class FirestoreRemindersRepository(
    private val authRepository: AuthRepository,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : RemindersRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun requireUserId(): String =
        authRepository.currentUserId
            ?: error("User not logged in")

    private fun remindersCollection(ownerId: String = requireUserId()) =
        firestore.collection(USERS).document(ownerId).collection(REMINDERS)

    private fun reminderDocument(key: ReminderKey) =
        remindersCollection(key.ownerId).document(key.reminderId)

    /**
     * Los propios se leen por ruta y los ajenos por `collectionGroup`: asi los recordatorios
     * anteriores al reparto, que no tienen `contributors`, siguen apareciendo sin migracion.
     */
    override suspend fun getReminders(): Result<List<Reminder>> {
        return try {
            val userId = requireUserId()
            val own = remindersCollection(userId)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            // Si las reglas o el indice de grupo todavia no estan desplegados, la consulta de
            // compartidos falla: los recordatorios propios tienen que seguir viendose igual.
            val shared = runCatching {
                firestore.collectionGroup(REMINDERS)
                    .whereArrayContains(CONTRIBUTORS, userId)
                    .get()
                    .await()
                    .documents
            }.getOrDefault(emptyList())
            val reminders = (own.documents + shared)
                .map { document -> document.toReminder(userId) }
                .distinctBy(Reminder::key)
            Result.success(reminders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
        return try {
            val userId = requireUserId()
            val collection = remindersCollection(userId)
            val existing = collection.get().await()
            val nextOrder = existing.documents
                .filterNot { it.getBoolean("completed") ?: false }
                .maxOfOrNull { it.getLong("order")?.toInt() ?: 0 }
                ?.plus(1)
                ?: 0
            val docRef = collection.document()
            val data = hashMapOf<String, Any?>(
                "text" to text,
                "dueDate" to due?.date?.toString(),
                "dueTime" to due?.time?.toString(),
                "completed" to false,
                "completedAt" to null,
                "order" to nextOrder,
                "date" to nowProvider().toFirebaseTimestamp(),
                CONTRIBUTORS to listOf(userId),
            )
            docRef.set(data).await()
            Result.success(docRef.get().await().toReminder(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateReminder(
        key: ReminderKey,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> {
        return try {
            val docRef = reminderDocument(key)
            docRef.update(
                "text", text,
                "dueDate", due?.date?.toString(),
                "dueTime", due?.time?.toString(),
            ).await()
            Result.success(docRef.get().await().toReminder(requireUserId()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setReminderCompleted(
        key: ReminderKey,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> {
        return try {
            reminderDocument(key).update(
                "completed", completed,
                "completedAt", completedAt?.toFirebaseTimestamp(),
                "order", order,
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            reminderKeysInOrder.forEachIndexed { index, key ->
                batch.update(reminderDocument(key), "order", index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit> {
        return try {
            if (reminderKeys.isNotEmpty()) {
                val batch = firestore.batch()
                reminderKeys.forEach { key ->
                    batch.delete(reminderDocument(key))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit> =
        updateContributors(reminderId) { contributors -> contributors + friendUserId }

    override suspend fun unshareReminder(reminderId: String, friendUserId: String): Result<Unit> =
        updateContributors(reminderId) { contributors ->
            contributors.filterNot { contributorId -> contributorId == friendUserId }
        }

    override suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit> {
        return try {
            val userId = requireUserId()
            if (key.ownerId == userId) error("Owner cannot leave own reminder")
            val docRef = reminderDocument(key)
            val snapshot = docRef.get().await()
            if (userId !in snapshot.contributors(key.ownerId)) {
                error("Reminder not found")
            }
            docRef.update(
                CONTRIBUTORS,
                effectiveReminderContributors(
                    ownerId = key.ownerId,
                    contributors = snapshot.contributors(key.ownerId).filterNot { contributorId ->
                        contributorId == userId
                    },
                ),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateContributors(
        reminderId: String,
        update: (List<String>) -> List<String>,
    ): Result<Unit> {
        return try {
            val userId = requireUserId()
            val docRef = reminderDocument(ReminderKey(userId, reminderId))
            val snapshot = docRef.get().await()
            docRef.update(
                CONTRIBUTORS,
                effectiveReminderContributors(userId, update(snapshot.contributors(userId))),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS = "users"
        private const val REMINDERS = "reminders"
        private const val CONTRIBUTORS = "contributors"
    }
}

private fun Instant.toFirebaseTimestamp(): Timestamp =
    Timestamp(Date(toEpochMilliseconds()))

private fun DocumentSnapshot.ownerId(): String =
    reference.parent.parent?.id.orEmpty()

private fun DocumentSnapshot.contributors(ownerId: String): List<String> =
    effectiveReminderContributors(
        ownerId = ownerId,
        contributors = (get("contributors") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
    )

private fun DocumentSnapshot.toReminder(currentUserId: String): Reminder {
    val dueDate = getString("dueDate")?.let(LocalDate::parse)
    val ownerId = ownerId()
    val contributors = contributors(ownerId)
    return Reminder(
        id = id,
        ownerId = ownerId,
        text = getString("text").orEmpty(),
        createdAt = getTimestamp("date")
            ?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
            ?: Instant.fromEpochMilliseconds(0),
        due = dueDate?.let { date ->
            ReminderDue(date, getString("dueTime")?.let(LocalTime::parse))
        },
        completed = getBoolean("completed") ?: false,
        completedAt = getTimestamp("completedAt")
            ?.let { Instant.fromEpochMilliseconds(it.toDate().time) },
        order = getLong("order")?.toInt() ?: 0,
        contributors = contributors,
        isShared = ownerId != currentUserId || contributors.size > 1,
    )
}
