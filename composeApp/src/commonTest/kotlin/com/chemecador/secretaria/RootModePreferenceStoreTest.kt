package com.chemecador.secretaria

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class RootModePreferenceStoreTest {

    @Test
    fun load_withoutAnythingStored_fallsBackToLists() = runTest {
        val store = RootModePreferenceStore(FakeUiPreferences())

        assertEquals(SecretariaRootMode.LISTS, store.load())
    }

    @Test
    fun load_returnsTheSavedMode() = runTest {
        val store = RootModePreferenceStore(FakeUiPreferences())

        store.save(SecretariaRootMode.REMINDERS)

        assertEquals(SecretariaRootMode.REMINDERS, store.load())
    }

    @Test
    fun clear_returnsToTheDefaultMode() = runTest {
        val store = RootModePreferenceStore(FakeUiPreferences())
        store.save(SecretariaRootMode.REMINDERS)

        store.clear()

        assertEquals(SecretariaRootMode.LISTS, store.load())
    }

    /** Un valor de una version futura o corrupto no puede dejar la app sin pantalla de inicio. */
    @Test
    fun load_withAnUnknownValue_fallsBackToLists() = runTest {
        val preferences = FakeUiPreferences()
        preferences.putString("app.root_mode", "CALENDAR")

        assertEquals(SecretariaRootMode.LISTS, RootModePreferenceStore(preferences).load())
    }
}

internal class FakeUiPreferences : UiPreferences {

    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
