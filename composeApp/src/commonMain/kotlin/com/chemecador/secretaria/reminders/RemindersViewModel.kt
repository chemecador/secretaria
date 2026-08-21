package com.chemecador.secretaria.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.noteslists.ListCollaborator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class RemindersViewModel(
    private val repository: RemindersRepository,
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(RemindersState())
    val state: StateFlow<RemindersState> = _state.asStateFlow()

    private var knownFriendsByUserId: Map<String, FriendSummary> = emptyMap()

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

    /**
     * [shareWith] llega del propio dialogo de creacion: el reparto no puede ir en la misma
     * escritura porque `contributors` necesita el documento ya creado, asi que se aplica despues.
     */
    fun createReminder(
        text: String,
        due: ReminderDue? = null,
        shareWith: List<FriendSummary> = emptyList(),
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            repository.createReminder(trimmedText, due)
                .onSuccess { created ->
                    shareNewReminder(created.id, shareWith)
                    fetchReminders()
                }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    /**
     * Un fallo aqui no deshace nada: el recordatorio ya existe y solo se ha quedado sin repartir,
     * asi que se avisa por el snackbar de compartir en lugar de dar la creacion por fallida.
     */
    private suspend fun shareNewReminder(reminderId: String, friends: List<FriendSummary>) {
        friends.distinctBy(FriendSummary::userId).forEach { friend ->
            knownFriendsByUserId = knownFriendsByUserId + (friend.userId to friend)
            repository.shareReminder(reminderId, friend.userId)
                .onFailure {
                    _state.update {
                        it.copy(
                            shareFeedback = ReminderSharingFeedback(
                                friendName = friend.name,
                                action = ReminderSharingAction.SHARED,
                                isSuccess = false,
                            ),
                        )
                    }
                }
        }
    }

    fun updateReminder(key: ReminderKey, text: String, due: ReminderDue?) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            repository.updateReminder(key, trimmedText, due)
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
    fun setReminderCompleted(key: ReminderKey, completed: Boolean) {
        val previousReminders = _state.value.reminders
        val target = previousReminders.firstOrNull { it.key == key } ?: return
        if (target.completed == completed) return

        val completedAt = if (completed) nowProvider() else null
        val order = if (completed) {
            target.order
        } else {
            // Al restaurar va al final de los pendientes para no chocar con un `order` existente.
            (previousReminders.filterNot(Reminder::completed).maxOfOrNull(Reminder::order) ?: -1) + 1
        }
        val updatedReminders = previousReminders.map { reminder ->
            if (reminder.key == key) {
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
            repository.setReminderCompleted(key, completed, completedAt, order)
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

    /** [reminderKeysInOrder] son solo los pendientes: los completados no se reordenan. */
    fun reorderReminders(reminderKeysInOrder: List<ReminderKey>) {
        val previousReminders = _state.value.reminders
        val currentPending = previousReminders.filterNot(Reminder::completed)
            .sortedWith(compareBy(Reminder::order, Reminder::createdAt, Reminder::id))
        val reorderedPending = currentPending.applyReminderOrder(reminderKeysInOrder) ?: return

        if (currentPending.map(Reminder::key) == reorderedPending.map(Reminder::key)) {
            return
        }

        val reorderedByKey = reorderedPending.associateBy(Reminder::key)
        val updatedReminders = previousReminders.map { reminder ->
            reorderedByKey[reminder.key] ?: reminder
        }

        _state.update { it.copy(reminders = updatedReminders, errorMessage = null) }

        viewModelScope.launch {
            repository.reorderReminders(reminderKeysInOrder)
                .onFailure { throwable ->
                    _state.update {
                        it.copy(reminders = previousReminders, errorMessage = throwable.message)
                    }
                }
        }
    }

    fun deleteReminder(key: ReminderKey) {
        viewModelScope.launch {
            repository.deleteReminders(listOf(key))
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

    /**
     * Al crear todavia no hay documento contra el que filtrar, asi que se ofrecen todos los
     * amigos y la seleccion la guarda la pantalla hasta que el recordatorio existe.
     */
    fun loadShareableFriendsForNewReminder() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoadingShareableFriends = true,
                    shareableFriends = emptyList(),
                    shareErrorMessage = null,
                    shareFeedback = null,
                )
            }
            friendsRepository.getFriends()
                .onSuccess { friends ->
                    cacheFriends(friends)
                    _state.update {
                        it.copy(
                            isLoadingShareableFriends = false,
                            shareableFriends = friends.sortedByName(),
                            shareErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoadingShareableFriends = false,
                            shareableFriends = emptyList(),
                            shareErrorMessage = throwable.message,
                        )
                    }
                }
        }
    }

    fun loadShareableFriends(reminder: Reminder) {
        viewModelScope.launch {
            requireOwnedReminder(reminder)
                .fold(
                    onSuccess = { currentReminder ->
                        _state.update {
                            it.copy(
                                isLoadingShareableFriends = true,
                                shareableFriends = emptyList(),
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        friendsRepository.getFriends()
                            .onSuccess { friends ->
                                cacheFriends(friends)
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = friends
                                            .filterNot { friend ->
                                                friend.userId in currentReminder.sharedWithUserIds
                                            }
                                            .sortedByName(),
                                        collaboratorsByReminderId = it.collaboratorsByReminderId.updated(
                                            reminderId = currentReminder.id,
                                            collaborators = buildCollaborators(currentReminder),
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = emptyList(),
                                        collaboratorsByReminderId = it.collaboratorsByReminderId.updated(
                                            reminderId = currentReminder.id,
                                            collaborators = buildCollaborators(
                                                currentReminder,
                                                friendsByUserId = emptyMap(),
                                            ),
                                        ),
                                        shareErrorMessage = throwable.message,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.copy(shareErrorMessage = throwable.message) }
                    },
                )
        }
    }

    fun shareReminder(reminder: Reminder, friend: FriendSummary) {
        viewModelScope.launch {
            requireOwnedReminder(reminder)
                .fold(
                    onSuccess = { currentReminder ->
                        knownFriendsByUserId = knownFriendsByUserId + (friend.userId to friend)
                        _state.update {
                            it.copy(
                                isUpdatingSharing = true,
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        repository.shareReminder(currentReminder.id, friend.userId)
                            .onSuccess {
                                val updatedReminder = updateLocalReminder(currentReminder.key) { existing ->
                                    existing.withContributors(existing.contributors + friend.userId)
                                } ?: currentReminder.withContributors(
                                    currentReminder.contributors + friend.userId,
                                )
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareableFriends = it.shareableFriends.filterNot { candidate ->
                                            candidate.userId == friend.userId
                                        },
                                        collaboratorsByReminderId = it.collaboratorsByReminderId.updated(
                                            reminderId = currentReminder.id,
                                            collaborators = buildCollaborators(updatedReminder),
                                        ),
                                        shareFeedback = ReminderSharingFeedback(
                                            friendName = friend.name,
                                            action = ReminderSharingAction.SHARED,
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareErrorMessage = throwable.message,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.copy(shareErrorMessage = throwable.message) }
                    },
                )
        }
    }

    fun unshareReminder(reminder: Reminder, collaborator: ListCollaborator) {
        viewModelScope.launch {
            requireOwnedReminder(reminder)
                .fold(
                    onSuccess = { currentReminder ->
                        _state.update {
                            it.copy(
                                isUpdatingSharing = true,
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        repository.unshareReminder(currentReminder.id, collaborator.userId)
                            .onSuccess {
                                val updatedReminder = updateLocalReminder(currentReminder.key) { existing ->
                                    existing.withContributors(
                                        existing.contributors.filterNot { contributorId ->
                                            contributorId == collaborator.userId
                                        },
                                    )
                                } ?: currentReminder.withContributors(
                                    currentReminder.contributors.filterNot { contributorId ->
                                        contributorId == collaborator.userId
                                    },
                                )
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareableFriends = it.shareableFriends.withFriend(
                                            knownFriendsByUserId[collaborator.userId],
                                        ),
                                        collaboratorsByReminderId = it.collaboratorsByReminderId.updated(
                                            reminderId = currentReminder.id,
                                            collaborators = buildCollaborators(updatedReminder),
                                        ),
                                        shareFeedback = ReminderSharingFeedback(
                                            friendName = collaborator.name,
                                            action = ReminderSharingAction.UNSHARED,
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareErrorMessage = throwable.message,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.copy(shareErrorMessage = throwable.message) }
                    },
                )
        }
    }

    fun leaveSharedReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.leaveSharedReminder(reminder.key)
                .onSuccess {
                    _state.update {
                        it.copy(
                            reminders = it.reminders.filterNot { item -> item.key == reminder.key },
                            feedback = ReminderFeedback(
                                action = ReminderFeedbackAction.LEFT_SHARED,
                                isSuccess = true,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            errorMessage = throwable.message,
                            feedback = ReminderFeedback(
                                action = ReminderFeedbackAction.LEFT_SHARED,
                                isSuccess = false,
                            ),
                        )
                    }
                }
        }
    }

    fun clearShareState() {
        _state.update {
            it.copy(
                shareableFriends = emptyList(),
                isLoadingShareableFriends = false,
                isUpdatingSharing = false,
                shareErrorMessage = null,
            )
        }
    }

    fun consumeFeedback() {
        _state.update { it.copy(feedback = null) }
    }

    fun consumeShareFeedback() {
        _state.update { it.copy(shareFeedback = null) }
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
                val reminders = if (purge) purgeExpiredReminders(items) else items
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    reminders = reminders,
                    errorMessage = null,
                    collaboratorsByReminderId = emptyMap(),
                )
                refreshCollaborators(reminders)
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
                        collaboratorsByReminderId = emptyMap(),
                    )
                }
            }
    }

    /**
     * Mantenimiento oportunista: un fallo al borrar no pone error ni oculta nada, porque no es
     * una accion que el usuario haya pedido.
     */
    private suspend fun purgeExpiredReminders(items: List<Reminder>): List<Reminder> {
        val currentUserId = authRepository.currentUserId ?: return items
        val expiredKeys = items.remindersToPurge(nowProvider(), currentUserId)
        if (expiredKeys.isEmpty()) return items

        return repository.deleteReminders(expiredKeys).fold(
            onSuccess = {
                val expired = expiredKeys.toSet()
                items.filterNot { it.key in expired }
            },
            onFailure = { items },
        )
    }

    /** Solo se resuelven nombres de lo propio compartido: en lo ajeno no hay nada que gestionar. */
    private suspend fun refreshCollaborators(items: List<Reminder>) {
        val currentUserId = authRepository.currentUserId ?: return
        val ownedSharedReminders = items.filter { reminder ->
            reminder.ownerId == currentUserId && reminder.sharedWithUserIds.isNotEmpty()
        }
        if (ownedSharedReminders.isEmpty()) {
            _state.update { it.copy(collaboratorsByReminderId = emptyMap()) }
            return
        }

        friendsRepository.getFriends()
            .onSuccess { friends ->
                cacheFriends(friends)
                _state.update { state ->
                    state.copy(
                        collaboratorsByReminderId = ownedSharedReminders.associate { reminder ->
                            reminder.id to buildCollaborators(reminder)
                        }.filterValues { collaborators -> collaborators.isNotEmpty() },
                    )
                }
            }
            .onFailure {
                _state.update { it.copy(collaboratorsByReminderId = emptyMap()) }
            }
    }

    private fun requireOwnedReminder(reminder: Reminder): Result<Reminder> {
        val currentReminder = findCurrentReminder(reminder)
        return if (currentReminder.ownerId == authRepository.currentUserId) {
            Result.success(currentReminder)
        } else {
            Result.failure(IllegalStateException(OWNERSHIP_ERROR_MESSAGE))
        }
    }

    private fun findCurrentReminder(reminder: Reminder): Reminder =
        _state.value.reminders.firstOrNull { item -> item.key == reminder.key } ?: reminder

    private fun cacheFriends(friends: List<FriendSummary>) {
        knownFriendsByUserId = friends.associateBy { friend -> friend.userId }
    }

    private fun buildCollaborators(
        reminder: Reminder,
        friendsByUserId: Map<String, FriendSummary> = knownFriendsByUserId,
    ): List<ListCollaborator> = reminder.sharedWithUserIds
        .map { userId ->
            val friend = friendsByUserId[userId]
            ListCollaborator(
                userId = userId,
                name = friend?.name ?: userId,
                isResolvedName = friend != null,
            )
        }
        .sortedBy { collaborator -> collaborator.name.lowercase() }

    private fun updateLocalReminder(
        key: ReminderKey,
        update: (Reminder) -> Reminder,
    ): Reminder? {
        var updatedReminder: Reminder? = null
        _state.update { state ->
            state.copy(
                reminders = state.reminders.map { reminder ->
                    if (reminder.key == key) {
                        update(reminder).also { updatedReminder = it }
                    } else {
                        reminder
                    }
                },
            )
        }
        return updatedReminder
    }

    private companion object {
        const val OWNERSHIP_ERROR_MESSAGE = "Only the owner can manage sharing"
    }
}

private fun List<FriendSummary>.sortedByName(): List<FriendSummary> =
    sortedBy { friend -> friend.name.lowercase() }

private fun List<FriendSummary>.withFriend(friend: FriendSummary?): List<FriendSummary> =
    if (friend == null || any { candidate -> candidate.userId == friend.userId }) {
        this
    } else {
        (this + friend).sortedByName()
    }

private fun Map<String, List<ListCollaborator>>.updated(
    reminderId: String,
    collaborators: List<ListCollaborator>,
): Map<String, List<ListCollaborator>> =
    if (collaborators.isEmpty()) {
        this - reminderId
    } else {
        this + (reminderId to collaborators)
    }
