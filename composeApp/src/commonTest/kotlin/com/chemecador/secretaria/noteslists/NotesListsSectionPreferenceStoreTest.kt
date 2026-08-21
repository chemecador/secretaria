package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.FakeUiPreferences
import com.chemecador.secretaria.RootModePreferenceStore
import com.chemecador.secretaria.SecretariaRootMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class NotesListsSectionPreferenceStoreTest {

    @Test
    fun load_withoutAnythingStored_fallsBackToMine() = runTest {
        val store = NotesListsSectionPreferenceStore(FakeUiPreferences())

        assertEquals(NotesListsSection.MINE, store.load())
    }

    @Test
    fun saveAndClear_roundTripThroughThePreferences() = runTest {
        val store = NotesListsSectionPreferenceStore(FakeUiPreferences())

        store.save(NotesListsSection.SHARED)
        assertEquals(NotesListsSection.SHARED, store.load())

        store.clear()
        assertEquals(NotesListsSection.MINE, store.load())
    }

    /** Las dos preferencias comparten almacen: no pueden pisarse la clave. */
    @Test
    fun theTwoStores_doNotShareAKey() = runTest {
        val preferences = FakeUiPreferences()

        NotesListsSectionPreferenceStore(preferences).save(NotesListsSection.SHARED)
        RootModePreferenceStore(preferences).save(SecretariaRootMode.REMINDERS)

        assertEquals(NotesListsSection.SHARED, NotesListsSectionPreferenceStore(preferences).load())
        assertEquals(SecretariaRootMode.REMINDERS, RootModePreferenceStore(preferences).load())
    }
}
