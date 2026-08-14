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
        return Result.success(reminders.toList())
    }

    override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
        ensureSeeded()
        val nextOrder = (reminders.filterNot(Reminder::completed).maxOfOrNull(Reminder::order) ?: -1) + 1
        val newReminder = Reminder(
            id = "reminder-${++lastId}",
            text = text,
            createdAt = nowProvider(),
            due = due,
            order = nextOrder,
        )
        reminders.add(newReminder)
        return Result.success(newReminder)
    }

    override suspend fun updateReminder(
        reminderId: String,
        text: String,
        due: ReminderDue?,
    ): Result<Reminder> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.id == reminderId }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        val updated = reminders[index].copy(text = text, due = due)
        reminders[index] = updated
        return Result.success(updated)
    }

    override suspend fun setReminderCompleted(
        reminderId: String,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit> {
        ensureSeeded()
        val index = reminders.indexOfFirst { it.id == reminderId }
        if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
        reminders[index] = reminders[index].copy(
            completed = completed,
            completedAt = completedAt,
            order = order,
        )
        return Result.success(Unit)
    }

    override suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit> {
        ensureSeeded()
        val pending = reminders.filterNot(Reminder::completed)
        val reordered = pending.applyReminderOrder(reminderIdsInOrder)
            ?: return Result.failure(IllegalStateException("Invalid reminder order"))
        val reorderedById = reordered.associateBy(Reminder::id)
        reminders.indices.forEach { index ->
            reorderedById[reminders[index].id]?.let { reminders[index] = it }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteReminders(reminderIds: List<String>): Result<Unit> {
        ensureSeeded()
        reminders.removeAll { it.id in reminderIds }
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
     * ejecute: uno vencido, uno de todo el dia, uno con hora, uno recien completado y uno
     * completado hace 40 dias que el purgado debe eliminar al cargar.
     */
    private fun seedReminders(now: Instant): List<Reminder> {
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return listOf(
            Reminder(
                id = "reminder-1",
                text = "Llamar al fontanero",
                createdAt = now - 2.days,
                due = ReminderDue(today, LocalTime.parse("18:00")),
                order = 0,
            ),
            Reminder(
                id = "reminder-2",
                text = "Comprar pilas AA",
                createdAt = now - 3.days,
                order = 1,
            ),
            Reminder(
                id = "reminder-3",
                text = "Leche, huevos y pan",
                createdAt = now - 1.days,
                order = 2,
            ),
            Reminder(
                id = "reminder-4",
                text = "Renovar el DNI",
                createdAt = now - 10.days,
                due = ReminderDue(today.plus(9, DateTimeUnit.DAY)),
                order = 3,
            ),
            Reminder(
                id = "reminder-5",
                text = "Devolver el libro a la biblioteca",
                createdAt = now - 20.days,
                due = ReminderDue(today.minus(8, DateTimeUnit.DAY)),
                order = 4,
            ),
            Reminder(
                id = "reminder-6",
                text = "Recoger el paquete",
                createdAt = now - 5.days,
                completed = true,
                completedAt = now - 2.days,
                order = 5,
            ),
            Reminder(
                id = "reminder-7",
                text = "Pagar el seguro del coche",
                createdAt = now - 60.days,
                completed = true,
                completedAt = now - 40.days,
                order = 6,
            ),
        )
    }
}
