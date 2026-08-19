package com.chemecador.secretaria.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
actual fun rememberNotePhotoPickerController(
    onResult: (NotePhotoPickerResult) -> Unit,
): NotePhotoPickerController? {
    val context = LocalContext.current
    val compressor = remember(context.applicationContext) {
        AndroidNotePhotoCompressor(context.applicationContext)
    }
    val currentOnResult by rememberUpdatedState(onResult)
    val coroutineScope = rememberCoroutineScope()
    val session = remember { PhotoPickerSession() }

    DisposableEffect(session) {
        onDispose {
            if (session.isActive) {
                session.isActive = false
                currentOnResult(NotePhotoPickerResult.Cancelled)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (!session.isActive) return@rememberLauncherForActivityResult
        if (uri == null) {
            session.isActive = false
            currentOnResult(NotePhotoPickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            try {
                val prepared = compressor.prepare(uri)
                if (!session.isActive) return@launch
                session.isActive = false
                prepared
                    .onSuccess { photo ->
                        currentOnResult(NotePhotoPickerResult.Selected(photo))
                    }
                    .onFailure { error ->
                        currentOnResult(NotePhotoPickerResult.Failed(error.toNotePhotosError()))
                    }
            } catch (error: CancellationException) {
                if (session.isActive) {
                    session.isActive = false
                    currentOnResult(NotePhotoPickerResult.Cancelled)
                }
                throw error
            }
        }
    }

    return remember(picker) {
        object : NotePhotoPickerController {
            override fun launch() {
                if (session.isActive) return
                session.isActive = true
                runCatching {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }.onFailure { error ->
                    session.isActive = false
                    currentOnResult(
                        NotePhotoPickerResult.Failed(
                            NotePhotosError.Repository(error.message),
                        ),
                    )
                }
            }
        }
    }
}

private class PhotoPickerSession(
    var isActive: Boolean = false,
)
