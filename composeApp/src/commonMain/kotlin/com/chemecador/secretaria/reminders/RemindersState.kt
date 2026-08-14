package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.noteslists.ListCollaborator
import kotlin.time.Instant

data class RemindersState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val errorMessage: String? = null,
    val feedback: ReminderFeedback? = null,
    /** Reutiliza [ListCollaborator], que ya es el modelo generico de "quien tiene acceso". */
    val collaboratorsByReminderId: Map<String, List<ListCollaborator>> = emptyMap(),
    val shareableFriends: List<FriendSummary> = emptyList(),
    val isLoadingShareableFriends: Boolean = false,
    val isUpdatingSharing: Boolean = false,
    val shareErrorMessage: String? = null,
    val shareFeedback: ReminderSharingFeedback? = null,
)

/**
 * Orden manual del usuario: nada se recoloca solo, ni siquiera lo vencido. Con recordatorios
 * compartidos el `order` puede empatar entre propietarios distintos, asi que se desempata por
 * fecha de creacion e id para que el listado no baile entre cargas.
 */
internal val RemindersState.pendingReminders: List<Reminder>
    get() = reminders.filterNot(Reminder::completed)
        .sortedWith(compareBy(Reminder::order, Reminder::createdAt, Reminder::id))

internal val RemindersState.completedReminders: List<Reminder>
    get() = reminders.filter(Reminder::completed)
        .sortedByDescending { it.completedAt ?: Instant.DISTANT_PAST }
