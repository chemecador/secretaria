package com.chemecador.secretaria.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FakeRemindersRepository(
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : RemindersRepository {

    private val reminders = mutableListOf<Reminder>()
    private var seeded = false
    private var lastId = 0

    override suspend fun getReminders(): Result<List<Reminder>> {
        ensureSeeded()
        return Result.success(
            reminders.filter { reminder -> CURRENT_USER_ID in reminder.contributors },
        )
    }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
        ensureSeeded()
        val nextOrder = (reminders.filterNot(Reminder::completed).maxOfOrNull(Reminder::order) ?: -1) + 1
        val newReminder = Reminder(
            id = "reminder-${++lastId}",
            ownerId = CURRENT_USER_ID,
            text = text,
            createdAt = nowProvider(),
            due = due,
            order = nextOrder,
        )
        reminders.add(newReminder)
        return Result.success(newReminder)
    }

    override suspend fun updateReminder(
        key: ReminderKey,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.key == key }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        val updated = reminders[index].copy(text = text, due = due)
        reminders[index] = updated
        return Result.success(updated)
    }

    override suspend fun setReminderCompleted(
        key: ReminderKey,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.key == key }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        reminders[index] = reminders[index].copy(
            completed = completed,
            completedAt = completedAt,
            order = order,
        )
        return Result.success(Unit)
    }

    override suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit> {
        ensureSeeded()
        val pending = reminders.filterNot(Reminder::completed)
        val reordered = pending.applyReminderOrder(reminderKeysInOrder)
            ?: return Result.failure(IllegalStateException("Invalid reminder order"))
        val reorderedByKey = reordered.associateBy(Reminder::key)
        reminders.indices.forEach { index ->
            reorderedByKey[reminders[index].key]?.let { reminders[index] = it }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit> {
        ensureSeeded()
        reminders.removeAll { it.key in reminderKeys }
        return Result.success(Unit)
    }

    override suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.id == reminderId && it.ownerId == CURRENT_USER_ID }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        reminders[index] = reminders[index].withContributors(
            reminders[index].contributors + friendUserId,
        )
        return Result.success(Unit)
    }

    override suspend fun unshareReminder(reminderId: String, friendUserId: String): Result<Unit> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.id == reminderId && it.ownerId == CURRENT_USER_ID }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        reminders[index] = reminders[index].withContributors(
            reminders[index].contributors.filterNot { contributorId -> contributorId == friendUserId },
        )
        return Result.success(Unit)
    }

    override suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit> {
        ensureSeeded()
        if (key.ownerId == CURRENT_USER_ID) {
            return Result.failure(IllegalStateException("Owner cannot leave own reminder"))
        }
        val index = reminders.indexOfFirst { it.key == key }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        reminders[index] = reminders[index].withContributors(
            reminders[index].contributors.filterNot { contributorId -> contributorId == CURRENT_USER_ID },
        )
        return Result.success(Unit)
    }

    private fun ensureSeeded() {
        if (seeded) return
        reminders.addAll(seedReminders(nowProvider()))
        lastId = reminders.size
        seeded = true
    }

    /**
     * Semilla relativa al reloj para que los casos sigan teniendo sentido se ejecute cuando se
     * ejecute: uno vencido, uno de todo el dia, uno con hora, uno compartido por un amigo, uno
     * recien completado y uno completado hace 40 dias que el purgado debe eliminar al cargar.
     */
    private fun seedReminders(now: Instant): List<Reminder> {
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return listOf(
            Reminder(
                id = "reminder-1",
                ownerId = CURRENT_USER_ID,
                text = "Llamar al fontanero",
                createdAt = now - 2.days,
                due = ReminderDue(today, LocalTime.parse("18:00")),
                order = 0,
            ),
            Reminder(
                id = "reminder-2",
                ownerId = CURRENT_USER_ID,
                text = "Comprar pilas AA",
                createdAt = now - 3.days,
                order = 1,
            ),
            Reminder(
                id = "reminder-3",
                ownerId = CURRENT_USER_ID,
                text = "Leche, huevos y pan",
                createdAt = now - 1.days,
                order = 2,
                contributors = listOf(CURRENT_USER_ID, "Marta"),
                isShared = true,
            ),
            Reminder(
                id = "reminder-4",
                ownerId = CURRENT_USER_ID,
                text = "Renovar el DNI",
                createdAt = now - 10.days,
                due = ReminderDue(today.plus(9, DateTimeUnit.DAY)),
                order = 3,
            ),
            Reminder(
                id = "reminder-5",
                ownerId = CURRENT_USER_ID,
                text = "Devolver el libro a la biblioteca",
                createdAt = now - 20.days,
                due = ReminderDue(today.minus(8, DateTimeUnit.DAY)),
                order = 4,
            ),
            Reminder(
                id = "reminder-8",
                ownerId = "Marta",
                text = "Recoger las entradas del concierto",
                createdAt = now - 4.days,
                due = ReminderDue(today.plus(2, DateTimeUnit.DAY)),
                order = 5,
                contributors = listOf("Marta", CURRENT_USER_ID),
                isShared = true,
            ),
            Reminder(
                id = "reminder-6",
                ownerId = CURRENT_USER_ID,
                text = "Recoger el paquete",
                createdAt = now - 5.days,
                completed = true,
                completedAt = now - 2.days,
                order = 6,
            ),
            Reminder(
                id = "reminder-7",
                ownerId = CURRENT_USER_ID,
                text = "Pagar el seguro del coche",
                createdAt = now - 60.days,
                completed = true,
                completedAt = now - 40.days,
                order = 7,
            ),
        )
    }

    private companion object {
        const val CURRENT_USER_ID = "Alex"
    }
}
