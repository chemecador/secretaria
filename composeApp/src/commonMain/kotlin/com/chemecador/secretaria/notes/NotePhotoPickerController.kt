package com.chemecador.secretaria.notes

import androidx.compose.runtime.Composable

interface NotePhotoPickerController {
    fun launch()
}

/**
 * Returns null on targets where selecting and compressing note photos is not implemented yet.
 * The callback is stable across recompositions and receives an already sanitised JPEG on Android.
 */
@Composable
expect fun rememberNotePhotoPickerController(
    onResult: (NotePhotoPickerResult) -> Unit,
): NotePhotoPickerController?
