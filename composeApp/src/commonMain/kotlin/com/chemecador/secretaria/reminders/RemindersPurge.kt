package com.chemecador.secretaria.reminders

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal val COMPLETED_REMINDER_RETENTION: Duration = 30.days

/**
 * Devuelve los recordatorios completados propios que ya han superado la retencion.
 *
 * La regla vive aqui, pura y en commonMain, en lugar de replicarse en las cinco implementaciones
 * del repositorio: `getReminders()` ya devuelve pendientes y completados en una sola lectura,
 * asi que filtrar en cliente no cuesta ninguna llamada extra.
 *
 * Solo se purga lo propio: un compartido lo borrara su dueño cuando le toque el purgado, y un
 * invitado no tiene permiso para eliminarlo.
 */
internal fun List<Reminder>.remindersToPurge(
    now: Instant,
    ownerId: String,
    retention: Duration = COMPLETED_REMINDER_RETENTION,
): List<ReminderKey> {
    val threshold = now - retention
    return filter { reminder ->
        val completedAt = reminder.completedAt
        reminder.ownerId == ownerId &&
            reminder.completed &&
            completedAt != null &&
            completedAt < threshold
    }.map(Reminder::key)
}
