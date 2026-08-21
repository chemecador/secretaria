package com.chemecador.secretaria.widget

import android.content.Context
import com.chemecador.secretaria.login.FirebaseAuthRepository
import com.chemecador.secretaria.reminders.Reminder
import com.chemecador.secretaria.reminders.FirestoreRemindersRepository
import com.chemecador.secretaria.reminders.RemindersRepository
import kotlin.time.Clock

/**
 * Origen de datos del widget. No pasa por Koin a proposito: Koin se arranca dentro del
 * composable `App()`, asi que en el proceso del widget no hay contenedor al que pedirle nada.
 * Las dos implementaciones reales son publicas y su construccion es trivial.
 */
internal object RemindersWidgetData {

    private val authRepository by lazy { FirebaseAuthRepository() }

    private val repository: RemindersRepository by lazy {
        FirestoreRemindersRepository(authRepository)
    }

    /**
     * Un fallo de red no borra nada: la copia anterior sigue en pantalla, que es justo lo que
     * hace util a un widget sin conexion.
     */
    suspend fun refresh(context: Context) {
        val store = RemindersWidgetStore.get(context)
        if (authRepository.currentUserId == null) {
            store.save(RemindersWidgetSnapshot(isSignedIn = false))
            return
        }

        repository.getReminders().onSuccess { reminders ->
            store.save(
                RemindersWidgetSnapshot(
                    items = reminders.toWidgetItems(),
                    updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    isSignedIn = true,
                ),
            )
        }.onFailure {
            store.update { snapshot -> snapshot.copy(isSignedIn = true) }
        }
    }

    /**
     * Optimista con vuelta atras, igual que `RemindersViewModel.setReminderCompleted`: la fila
     * desaparece al tocarla y solo reaparece si Firestore rechaza la escritura.
     *
     * `completedAt` lo genera el cliente, como en los otros cinco targets, para que el purgado de
     * 30 dias compare contra el mismo valor que se persiste.
     */
    suspend fun complete(context: Context, ownerId: String, reminderId: String, order: Int) {
        val store = RemindersWidgetStore.get(context)
        val previous = store.current
        val item = previous.items.firstOrNull { it.ownerId == ownerId && it.id == reminderId }
            ?: return

        store.save(previous.copy(items = previous.items - item))
        RemindersWidgetUpdater.notifyChanged(context)

        repository.setReminderCompleted(
            key = item.key,
            completed = true,
            completedAt = Clock.System.now(),
            order = order,
        ).onFailure {
            store.save(previous)
            RemindersWidgetUpdater.notifyChanged(context)
        }
    }
}

/**
 * Mismo criterio que `RemindersState.pendingReminders`: orden manual del usuario, con la fecha de
 * creacion y el id como desempate, porque el `order` puede coincidir entre propietarios distintos.
 */
internal fun List<Reminder>.toWidgetItems(): List<RemindersWidgetItem> =
    filterNot(Reminder::completed)
        .sortedWith(compareBy(Reminder::order, Reminder::createdAt, Reminder::id))
        // Una lista larga se envia entera al lanzador por IPC; el widget no es para leerlo todo.
        .take(MAX_WIDGET_ITEMS)
        .map(Reminder::toWidgetItem)

internal const val MAX_WIDGET_ITEMS = 50
