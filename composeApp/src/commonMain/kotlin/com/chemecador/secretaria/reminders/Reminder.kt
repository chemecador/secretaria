package com.chemecador.secretaria.reminders

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class Reminder(
    val id: String,
    val text: String,
    val createdAt: Instant,
    val due: ReminderDue? = null,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val order: Int = 0,
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
