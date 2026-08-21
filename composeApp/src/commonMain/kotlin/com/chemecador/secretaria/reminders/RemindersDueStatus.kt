package com.chemecador.secretaria.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Un recordatorio vencido solo se resalta: nunca se reordena ni se archiva solo.
 *
 * Un vencimiento de todo el dia ([ReminderDue.time] nulo) no vence hasta que el dia entero
 * ha pasado, asi que algo fechado para hoy sin hora no cuenta como vencido.
 *
 * Publica porque el widget de Android la reutiliza: la regla del "todo el dia" es sutil y no
 * merece una segunda copia en `androidApp`.
 */
fun ReminderDue.isOverdue(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    val nowLocal = now.toLocalDateTime(timeZone)
    return if (time == null) {
        date < nowLocal.date
    } else {
        LocalDateTime(date, time) < nowLocal
    }
}
