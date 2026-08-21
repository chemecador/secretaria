package com.chemecador.secretaria.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Copia local de los recordatorios pendientes, en `SharedPreferences` como el resto de
 * preferencias de la app, porque DataStore todavia no esta en el proyecto.
 *
 * El `StateFlow` es lo que hace que el widget se repinte solo: la sesion de Glance lo recolecta,
 * asi que guardar aqui basta para que la lista cambie sin esperar al siguiente `update()`.
 */
internal class RemindersWidgetStore private constructor(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _snapshots = MutableStateFlow(read())
    val snapshots: StateFlow<RemindersWidgetSnapshot> = _snapshots.asStateFlow()

    val current: RemindersWidgetSnapshot get() = _snapshots.value

    fun save(snapshot: RemindersWidgetSnapshot) {
        preferences.edit { putString(KEY_SNAPSHOT, snapshot.toJson()) }
        _snapshots.value = snapshot
    }

    fun update(transform: (RemindersWidgetSnapshot) -> RemindersWidgetSnapshot) {
        save(transform(_snapshots.value))
    }

    fun clear() {
        preferences.edit { remove(KEY_SNAPSHOT) }
        _snapshots.value = RemindersWidgetSnapshot()
    }

    private fun read(): RemindersWidgetSnapshot =
        preferences.getString(KEY_SNAPSHOT, null)
            ?.let(RemindersWidgetSnapshot::fromJson)
            ?: RemindersWidgetSnapshot()

    companion object {
        private const val PREFERENCES_NAME = "secretaria.widget.reminders"
        private const val KEY_SNAPSHOT = "snapshot"

        @Volatile
        private var instance: RemindersWidgetStore? = null

        fun get(context: Context): RemindersWidgetStore =
            instance ?: synchronized(this) {
                instance ?: RemindersWidgetStore(context).also { instance = it }
            }
    }
}
