package com.chemecador.secretaria.notes

import androidx.compose.runtime.Composable

interface NotePhotoDownloadController {
    fun save(photo: NotePhoto, bytes: ByteArray)

    /**
     * Hands a photo saved earlier to whatever app the device views images with. Returns false when
     * nothing can open it, so the caller can say so instead of appearing to do nothing.
     */
    fun open(location: String): Boolean
}

/**
 * Returns null on targets where saving a note photo to the device is not implemented yet.
 * The callback is stable across recompositions and always reports a terminal result.
 */
@Composable
expect fun rememberNotePhotoDownloadController(
    onResult: (NotePhotoDownloadResult) -> Unit,
): NotePhotoDownloadController?
