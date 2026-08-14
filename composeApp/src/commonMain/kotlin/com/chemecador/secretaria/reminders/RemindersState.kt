package com.chemecador.secretaria.reminders

import kotlin.time.Instant

data class RemindersState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val errorMessage: String? = null,
    val feedback: ReminderFeedback? = null,
)

/** Orden manual del usuario: nada se recoloca solo, ni siquiera lo vencido. */
internal val RemindersState.pendingReminders: List<Reminder>
    get() = reminders.filterNot(Reminder::completed).sortedBy(Reminder::order)

internal val RemindersState.completedReminders: List<Reminder>
    get() = reminders.filter(Reminder::completed)
        .sortedByDescending { it.completedAt ?: Instant.DISTANT_PAST }
