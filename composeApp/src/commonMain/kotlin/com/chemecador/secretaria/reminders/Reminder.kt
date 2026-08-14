package com.chemecador.secretaria.reminders

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class Reminder(
    val id: String,
    val ownerId: String,
    val text: String,
    val createdAt: Instant,
    val due: ReminderDue? = null,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val order: Int = 0,
    /** Incluye siempre al propietario, igual que el array `contributors` de las listas. */
    val contributors: List<String> = listOf(ownerId),
    val isShared: Boolean = false,
)

/**
 * Fecha de vencimiento flotante: no es un instante absoluto, sino una designacion de calendario.
 * Se renderiza igual en cualquier plataforma y zona horaria.
 *
 * [time] nulo significa "todo el dia": nunca se debe pintar una hora en ese caso.
 */
data class ReminderDue(
    val date: LocalDate,
    val time: LocalTime? = null,
)

/**
 * Un recordatorio compartido vive en `users/{ownerId}/reminders`, asi que el id por si solo no
 * basta para escribir en el: hace falta la pareja propietario + id, como en `NotesListKey`.
 */
data class ReminderKey(
    val ownerId: String,
    val reminderId: String,
)

val Reminder.key: ReminderKey
    get() = ReminderKey(ownerId, id)

val Reminder.sharedWithUserIds: List<String>
    get() = contributors
        .distinct()
        .filterNot { contributorId -> contributorId == ownerId || contributorId.isBlank() }

internal fun Reminder.withContributors(contributors: List<String>): Reminder {
    val effective = effectiveReminderContributors(ownerId, contributors)
    return copy(
        contributors = effective,
        isShared = effective.size > 1,
    )
}

internal fun effectiveReminderContributors(
    ownerId: String,
    contributors: List<String>,
): List<String> =
    (listOf(ownerId) + contributors)
        .filter { contributorId -> contributorId.isNotBlank() }
        .distinct()
