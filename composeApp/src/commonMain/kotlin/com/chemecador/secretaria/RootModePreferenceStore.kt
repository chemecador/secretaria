package com.chemecador.secretaria

/**
 * Recuerda en que modo raiz estaba el usuario para que la app se reabra ahi y no siempre en
 * Listas. Solo guarda el modo, nunca la pantalla concreta: volver a un detalle de nota o a un
 * grupo tras dias cerrado seria mas desconcertante que util, y la pila de utilidades es
 * deliberadamente efimera.
 *
 * Se borra al cerrar sesion, igual que [com.chemecador.secretaria.noteslists.NotesListsSectionPreferenceStore].
 */
internal class RootModePreferenceStore(
    private val preferences: UiPreferences,
) {
    suspend fun load(): SecretariaRootMode =
        preferences.getString(KEY_ROOT_MODE).toRootModeOrDefault()

    suspend fun save(mode: SecretariaRootMode) {
        preferences.putString(KEY_ROOT_MODE, mode.name)
    }

    suspend fun clear() {
        preferences.remove(KEY_ROOT_MODE)
    }
}

internal fun String?.toRootModeOrDefault(): SecretariaRootMode =
    runCatching {
        SecretariaRootMode.valueOf(this ?: return SecretariaRootMode.LISTS)
    }.getOrDefault(SecretariaRootMode.LISTS)

private const val KEY_ROOT_MODE = "app.root_mode"
