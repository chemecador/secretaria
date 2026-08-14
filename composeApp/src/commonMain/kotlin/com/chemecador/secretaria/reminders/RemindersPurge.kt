package com.chemecador.secretaria.reminders

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal val COMPLETED_REMINDER_RETENTION: Duration = 30.days

/**
 * Devuelve los ids de los recordatorios completados que ya han superado la retencion.
 *
 * La regla vive aqui, pura y en commonMain, en lugar de replicarse en las cinco implementaciones
 * del repositorio: `getReminders()` ya devuelve pendientes y completados en una sola lectura,
 * asi que filtrar en cliente no cuesta ninguna llamada extra.
 */
internal fun List<Reminder>.remindersToPurge(
    now: Instant,
    retention: Duration = COMPLETED_REMINDER_RETENTION,
): List<String> {
    val threshold = now - retention
    return filter { reminder ->
        val completedAt = reminder.completedAt
        reminder.completed && completedAt != null && completedAt < threshold
    }.map(Reminder::id)
}
