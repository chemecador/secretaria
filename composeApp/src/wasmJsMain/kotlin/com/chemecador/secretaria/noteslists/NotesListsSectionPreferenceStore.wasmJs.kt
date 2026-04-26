package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore =
    NoOpNotesListsSectionPreferenceStore
