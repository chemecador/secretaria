package com.chemecador.secretaria.onboarding

import com.chemecador.secretaria.FakeUiPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OnboardingPreferenceStoreTest {

    @Test
    fun isCompleted_onAFreshInstall_isFalse() = runTest {
        assertFalse(OnboardingPreferenceStore(FakeUiPreferences()).isCompleted())
    }

    @Test
    fun markCompleted_isRemembered() = runTest {
        val store = OnboardingPreferenceStore(FakeUiPreferences())

        store.markCompleted()

        assertTrue(store.isCompleted())
    }

    /** Ante un valor corrupto se vuelve a ensenar la bienvenida, nunca a darla por vista. */
    @Test
    fun isCompleted_withAnUnexpectedValue_isFalse() = runTest {
        val preferences = FakeUiPreferences()
        preferences.putString("onboarding.completed", "maybe")

        assertFalse(OnboardingPreferenceStore(preferences).isCompleted())
    }
}
