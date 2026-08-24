package com.chemecador.secretaria.onboarding

import com.chemecador.secretaria.UiPreferences

/**
 * Recuerda si el usuario ya ha visto la bienvenida, para que solo aparezca en la primera apertura.
 *
 * A diferencia de los otros almacenes sobre [UiPreferences] este NO se borra al cerrar sesion:
 * describe el dispositivo, no la sesion. Volver a explicar la app a alguien que solo ha cambiado
 * de cuenta seria ruido, asi que no hay `clear()` a proposito.
 */
internal class OnboardingPreferenceStore(
    private val preferences: UiPreferences,
) {
    suspend fun isCompleted(): Boolean =
        preferences.getString(KEY_ONBOARDING_COMPLETED).toBoolean()

    suspend fun markCompleted() {
        preferences.putString(KEY_ONBOARDING_COMPLETED, true.toString())
    }
}

private const val KEY_ONBOARDING_COMPLETED = "onboarding.completed"
