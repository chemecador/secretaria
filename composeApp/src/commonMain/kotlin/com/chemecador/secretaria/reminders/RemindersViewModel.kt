package com.chemecador.secretaria.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class RemindersViewModel(
    private val repository: RemindersRepository,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(RemindersState())
    val state: StateFlow<RemindersState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            fetchReminders(purge = true)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fetchReminders(isRefresh = true)
        }
    }

    fun createReminder(text: String, due: ReminderDue? = null) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            repository.createReminder(trimmedText, due)
                .onSuccess { fetchReminders() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun updateReminder(reminderId: String, text: String, due: ReminderDue?) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            repository.updateReminder(reminderId, trimmedText, due)
                .onSuccess { fetchReminders() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    /**
     * Optimista con rollback: la fila desaparece bajo el dedo, y en los targets REST un
     * fire&refetch dejaria un salto visible. El valor de [Reminder.completedAt] lo genera el
     * ViewModel para que lo que se pinta sea identico a lo que se persiste y a lo que despues
     * compara el purgado de 30 dias.
     */
    fun setReminderCompleted(reminderId: String, completed: Boolean) {
        val previousReminders = _state.value.reminders
        val target = previousReminders.firstOrNull { it.id == reminderId } ?: return
        if (target.completed == completed) return

        val completedAt = if (completed) nowProvider() else null
        val order = if (completed) {
            target.order
        } else {
            // Al restaurar va al final de los pendientes para no chocar con un `order` existente.
            (previousReminders.filterNot(Reminder::completed).maxOfOrNull(Reminder::order) ?: -1) + 1
        }
        val updatedReminders = previousReminders.map { reminder ->
            if (reminder.id == reminderId) {
                reminder.copy(completed = completed, completedAt = completedAt, order = order)
            } else {
                reminder
            }
        }
        val action = if (completed) {
            ReminderFeedbackAction.COMPLETED
        } else {
            ReminderFeedbackAction.RESTORED
        }

        _state.update {
            it.copy(
                reminders = updatedReminders,
                errorMessage = null,
                feedback = ReminderFeedback(action = action, isSuccess = true),
            )
        }

        viewModelScope.launch {
            repository.setReminderCompleted(reminderId, completed, completedAt, order)
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            reminders = previousReminders,
                            errorMessage = throwable.message,
                            feedback = ReminderFeedback(action = action, isSuccess = false),
                        )
                    }
                }
        }
    }

    /** [reminderIdsInOrder] son solo los pendientes: los completados no se reordenan. */
    fun reorderReminders(reminderIdsInOrder: List<String>) {
        val previousReminders = _state.value.reminders
        val currentPending = previousReminders.filterNot(Reminder::completed).sortedBy(Reminder::order)
        val reorderedPending = currentPending.applyReminderOrder(reminderIdsInOrder) ?: return

        if (currentPending.map(Reminder::id) == reorderedPending.map(Reminder::id)) {
            return
        }

        val reorderedById = reorderedPending.associateBy(Reminder::id)
        val updatedReminders = previousReminders.map { reminder ->
            reorderedById[reminder.id] ?: reminder
        }

        _state.update { it.copy(reminders = updatedReminders, errorMessage = null) }

        viewModelScope.launch {
            repository.reorderReminders(reminderIdsInOrder)
                .onFailure { throwable ->
                    _state.update {
                        it.copy(reminders = previousReminders, errorMessage = throwable.message)
                    }
                }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminders(listOf(reminderId))
                .onSuccess { fetchReminders() }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            errorMessage = throwable.message,
                            feedback = ReminderFeedback(
                                action = ReminderFeedbackAction.DELETED,
                                isSuccess = false,
                            ),
                        )
                    }
                }
        }
    }

    fun consumeFeedback() {
        _state.update { it.copy(feedback = null) }
    }

    private suspend fun fetchReminders(isRefresh: Boolean = false, purge: Boolean = false) {
        _state.update { currentState ->
            if (isRefresh) {
                currentState.copy(isRefreshing = true, errorMessage = null)
            } else {
                currentState.copy(isLoading = true, errorMessage = null)
            }
        }

        repository.getReminders()
            .onSuccess { items ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    reminders = if (purge) purgeExpiredReminders(items) else items,
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                if (isRefresh) {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        errorMessage = throwable.message,
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        reminders = emptyList(),
                        errorMessage = throwable.message,
                    )
                }
            }
    }

    /**
     * Mantenimiento oportunista: un fallo al borrar no pone error ni oculta nada, porque no es
     * una accion que el usuario haya pedido.
     */
    private suspend fun purgeExpiredReminders(items: List<Reminder>): List<Reminder> {
        val expiredIds = items.remindersToPurge(nowProvider())
        if (expiredIds.isEmpty()) return items

        return repository.deleteReminders(expiredIds).fold(
            onSuccess = {
                val expired = expiredIds.toSet()
                items.filterNot { it.id in expired }
            },
            onFailure = { items },
        )
    }
}
