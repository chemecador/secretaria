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

    private fun remindersCollection() =
        firestore.collection(USERS).document(requireUserId()).collection(REMINDERS)

    override suspend fun getReminders(): Result<List<Reminder>> {
        return try {
            val snapshot = remindersCollection()
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            Result.success(snapshot.documents.map { document -> document.toReminder() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
        return try {
            val collection = remindersCollection()
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
            )
            docRef.set(data).await()
            Result.success(docRef.get().await().toReminder())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateReminder(
        reminderId: String,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> {
        return try {
            val docRef = remindersCollection().document(reminderId)
            docRef.update(
                "text", text,
                "dueDate", due?.date?.toString(),
                "dueTime", due?.time?.toString(),
            ).await()
            Result.success(docRef.get().await().toReminder())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setReminderCompleted(
        reminderId: String,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> {
        return try {
            remindersCollection().document(reminderId).update(
                "completed", completed,
                "completedAt", completedAt?.toFirebaseTimestamp(),
                "order", order,
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            reminderIdsInOrder.forEachIndexed { index, reminderId ->
                batch.update(remindersCollection().document(reminderId), "order", index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteReminders(reminderIds: List<String>): Result<Unit> {
        return try {
            if (reminderIds.isNotEmpty()) {
                val batch = firestore.batch()
                reminderIds.forEach { reminderId ->
                    batch.delete(remindersCollection().document(reminderId))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS = "users"
        private const val REMINDERS = "reminders"
    }
}

private fun Instant.toFirebaseTimestamp(): Timestamp =
    Timestamp(Date(toEpochMilliseconds()))

private fun DocumentSnapshot.toReminder(): Reminder {
    val dueDate = getString("dueDate")?.let(LocalDate::parse)
    return Reminder(
        id = id,
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
    )
}
