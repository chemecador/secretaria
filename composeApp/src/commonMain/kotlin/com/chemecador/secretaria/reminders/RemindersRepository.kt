package com.chemecador.secretaria.reminders

import kotlin.time.Instant

interface RemindersRepository {
    /** Devuelve los propios mas los que amigos han compartido con el usuario actual. */
    suspend fun getReminders(): Result<List<Reminder>>
    suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder>
    suspend fun updateReminder(key: ReminderKey, text: String, due: ReminderDue?): Result<Reminder>

    /**
     * [completedAt] y [order] los calcula el ViewModel para que el valor que se pinta de forma
     * optimista sea identico al persistido, y al que despues compara el purgado de 30 dias.
     */
    suspend fun setReminderCompleted(
        key: ReminderKey,
        completed: Boolean,
        completedAt: Instant?,
        order: Int,
    ): Result<Unit>

    suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit>

    /** Sirve tanto para el borrado individual como para el purgado de completados. */
    suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit>

    /** Solo el propietario comparte: por eso basta el id, sin propietario. */
    suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit>
    suspend fun unshareReminder(reminderId: String, friendUserId: String): Result<Unit>

    /** El invitado se saca a si mismo de `contributors` sin borrar el recordatorio del dueño. */
    suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit>
}
