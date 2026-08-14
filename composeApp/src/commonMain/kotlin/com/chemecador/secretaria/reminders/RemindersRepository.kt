package com.chemecador.secretaria.reminders

import kotlin.time.Instant

interface RemindersRepository {
    suspend fun getReminders(): Result<List<Reminder>>
    suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder>
    suspend fun updateReminder(reminderId: String, text: String, due: ReminderDue?): Result<Reminder>

    /**
     * [completedAt] y [order] los calcula el ViewModel para que el valor que se pinta de forma
     * optimista sea identico al persistido, y al que despues compara el purgado de 30 dias.
     */
    suspend fun setReminderCompleted(
        reminderId: String,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit>

    suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit>

    /** Sirve tanto para el borrado individual como para el purgado de completados. */
    suspend fun deleteReminders(reminderIds: List<String>): Result<Unit>
}
