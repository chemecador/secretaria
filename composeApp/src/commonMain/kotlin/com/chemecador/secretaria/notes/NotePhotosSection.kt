package com.chemecador.secretaria.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.note_photo_account_limit
import secretaria.composeapp.generated.resources.note_photo_add
import secretaria.composeapp.generated.resources.note_photo_close
import secretaria.composeapp.generated.resources.note_photo_daily_limit
import secretaria.composeapp.generated.resources.note_photo_daily_operation_limit
import secretaria.composeapp.generated.resources.note_photo_global_limit
import secretaria.composeapp.generated.resources.note_photo_delete
import secretaria.composeapp.generated.resources.note_photo_delete_message
import secretaria.composeapp.generated.resources.note_photo_delete_title
import secretaria.composeapp.generated.resources.note_photo_download
import secretaria.composeapp.generated.resources.note_photo_download_failed
import secretaria.composeapp.generated.resources.note_photo_download_saved
import secretaria.composeapp.generated.resources.note_photo_download_saving
import secretaria.composeapp.generated.resources.note_photo_dismiss
import secretaria.composeapp.generated.resources.note_photo_error_generic
import secretaria.composeapp.generated.resources.note_photo_image_too_large
import secretaria.composeapp.generated.resources.note_photo_invalid_image
import secretaria.composeapp.generated.resources.note_photo_loading
import secretaria.composeapp.generated.resources.note_photo_monthly_limit
import secretaria.composeapp.generated.resources.note_photo_network_error
import secretaria.composeapp.generated.resources.note_photo_note_limit
import secretaria.composeapp.generated.resources.note_photo_not_found
import secretaria.composeapp.generated.resources.note_photo_not_permitted
import secretaria.composeapp.generated.resources.note_photo_open
import secretaria.composeapp.generated.resources.note_photo_permission_denied
import secretaria.composeapp.generated.resources.note_photo_preparing
import secretaria.composeapp.generated.resources.note_photo_retry
import secretaria.composeapp.generated.resources.note_photo_storage_limit
import secretaria.composeapp.generated.resources.note_photo_thumbnail_failed
import secretaria.composeapp.generated.resources.note_photo_upload_unavailable
import secretaria.composeapp.generated.resources.note_photo_upload_session_expired
import secretaria.composeapp.generated.resources.note_photo_uploading
import secretaria.composeapp.generated.resources.note_photo_viewer
import secretaria.composeapp.generated.resources.note_photos_title

@Composable
fun NotePhotosSection(
    viewModel: NotePhotosViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val picker = rememberNotePhotoPickerController(viewModel::onPickerResult)
    val downloader = rememberNotePhotoDownloadController(viewModel::onDownloadResult)
    var photoPendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    if (!state.isSupported) return

    photoPendingDelete?.let { photoId ->
        AlertDialog(
            onDismissRequest = { photoPendingDelete = null },
            title = { Text(stringResource(Res.string.note_photo_delete_title)) },
            text = { Text(stringResource(Res.string.note_photo_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        photoPendingDelete = null
                        viewModel.deletePhoto(photoId)
                    },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.note_photo_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { photoPendingDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    NotePhotoViewer(
        state = state.viewerState,
        downloadState = state.downloadState,
        canDownload = downloader != null,
        onClose = viewModel::closeViewer,
        onRetry = { photoId -> viewModel.openPhoto(photoId) },
        onDownload = { ready ->
            if (viewModel.beginPhotoDownload()) downloader?.save(ready.photo, ready.bytes)
        },
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    Res.string.note_photos_title,
                    state.photos.size.toString(),
                    MAX_PHOTOS_PER_NOTE.toString(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.canUpload && picker != null) {
                FilledTonalButton(
                    onClick = {
                        if (viewModel.beginPhotoSelection()) picker.launch()
                    },
                    enabled = state.canAddPhoto,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.note_photo_add))
                }
            }
        }

        if (!state.canUpload) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.note_photo_upload_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isLoading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = state.photos,
                    key = { _, item -> item.photo.id },
                ) { index, item ->
                    NotePhotoThumbnail(
                        item = item,
                        index = index,
                        isDeleting = item.photo.id in state.deletingPhotoIds,
                        onOpen = { viewModel.openPhoto(item.photo.id) },
                        onRetry = { viewModel.retryThumbnail(item.photo.id) },
                        onDelete = { photoPendingDelete = item.photo.id },
                    )
                }
            }
        }

        NotePhotoUploadFeedback(
            state = state.uploadState,
            onRetry = viewModel::retryUpload,
            onDismiss = viewModel::dismissUploadError,
        )

        state.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            ErrorCard(
                message = error.toUserMessage(),
                onDismiss = viewModel::clearError,
            )
        }
    }
}

@Composable
private fun NotePhotoThumbnail(
    item: NotePhotoItemState,
    index: Int,
    isDeleting: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap = rememberDecodedBitmap(item.photo.id, item.thumbnailBytes)
    val openDescription = stringResource(Res.string.note_photo_open, (index + 1).toString())

    Card(
        modifier = Modifier.size(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = openDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = !isDeleting,
                                onClickLabel = openDescription,
                                onClick = onOpen,
                            ),
                    )
                }

                item.isThumbnailLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.BrokenImage, contentDescription = null)
                        Text(
                            text = stringResource(Res.string.note_photo_thumbnail_failed),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.note_photo_retry),
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                IconButton(
                    onClick = onDelete,
                    enabled = !isDeleting,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.note_photo_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (isDeleting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotePhotoUploadFeedback(
    state: NotePhotoUploadState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        NotePhotoUploadState.Idle -> Unit
        NotePhotoUploadState.Preparing,
        NotePhotoUploadState.Uploading,
        -> {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (state == NotePhotoUploadState.Preparing) {
                            Res.string.note_photo_preparing
                        } else {
                            Res.string.note_photo_uploading
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is NotePhotoUploadState.Failed -> {
            Spacer(Modifier.height(12.dp))
            ErrorCard(
                message = state.error.toUserMessage(),
                onRetry = onRetry.takeIf { state.canRetry },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            onRetry?.let {
                TextButton(onClick = it) {
                    Text(stringResource(Res.string.note_photo_retry))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.note_photo_dismiss),
                )
            }
        }
    }
}

@Composable
private fun NotePhotoViewer(
    state: NotePhotoViewerState,
    downloadState: NotePhotoDownloadState,
    canDownload: Boolean,
    onClose: () -> Unit,
    onRetry: (String) -> Unit,
    onDownload: (NotePhotoViewerState.Ready) -> Unit,
    onDismissDownloadFeedback: () -> Unit,
) {
    if (state == NotePhotoViewerState.Closed) return

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 640.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    NotePhotoViewerState.Closed -> Unit
                    is NotePhotoViewerState.Loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(Res.string.note_photo_loading))
                        }
                    }

                    is NotePhotoViewerState.Ready -> {
                        val bitmap = rememberDecodedBitmap(state.photo.id, state.bytes)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = stringResource(
                                    Res.string.note_photo_viewer,
                                ),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.note_photo_invalid_image),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }

                    is NotePhotoViewerState.Failed -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.BrokenImage, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(state.error.toUserMessage())
                            TextButton(onClick = { onRetry(state.photo.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.note_photo_retry))
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canDownload && state is NotePhotoViewerState.Ready) {
                            IconButton(
                                onClick = { onDownload(state) },
                                enabled = downloadState != NotePhotoDownloadState.Saving,
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(
                                        Res.string.note_photo_download,
                                    ),
                                )
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.note_photo_close),
                            )
                        }
                    }
                }

                NotePhotoDownloadFeedback(
                    state = downloadState,
                    onDismiss = onDismissDownloadFeedback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NotePhotoDownloadFeedback(
    state: NotePhotoDownloadState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == NotePhotoDownloadState.Idle) return

    // A save that worked needs no acknowledgement; only a failure waits for the user.
    LaunchedEffect(state) {
        if (state == NotePhotoDownloadState.Saved) {
            delay(SAVED_FEEDBACK_MILLIS)
            onDismiss()
        }
    }

    val isFailure = state is NotePhotoDownloadState.Failed
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isFailure) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isFailure) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == NotePhotoDownloadState.Saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = stringResource(
                    when (state) {
                        NotePhotoDownloadState.Saving -> Res.string.note_photo_download_saving
                        NotePhotoDownloadState.Saved -> Res.string.note_photo_download_saved
                        else -> Res.string.note_photo_download_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isFailure) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.note_photo_dismiss),
                    )
                }
            }
        }
    }
}

private const val SAVED_FEEDBACK_MILLIS = 2500L

@Composable
private fun rememberDecodedBitmap(id: String, bytes: ByteArray?): ImageBitmap? =
    remember(id, bytes) {
        bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }

@Composable
private fun NotePhotosError.toUserMessage(): String {
    val resource = when (this) {
        NotePhotosError.Unsupported -> Res.string.note_photo_upload_unavailable
        NotePhotosError.UploadNotAllowed -> Res.string.note_photo_not_permitted
        NotePhotosError.NoteLimitReached -> Res.string.note_photo_note_limit
        NotePhotosError.AccountPhotoLimitReached -> Res.string.note_photo_account_limit
        NotePhotosError.AccountStorageLimitReached -> Res.string.note_photo_storage_limit
        NotePhotosError.DailyUploadLimitReached -> Res.string.note_photo_daily_limit
        NotePhotosError.MonthlyUploadLimitReached -> Res.string.note_photo_monthly_limit
        NotePhotosError.DailyOperationLimitReached -> Res.string.note_photo_daily_operation_limit
        NotePhotosError.GlobalLimitReached -> Res.string.note_photo_global_limit
        NotePhotosError.UploadSessionExpired -> Res.string.note_photo_upload_session_expired
        NotePhotosError.InvalidImage -> Res.string.note_photo_invalid_image
        NotePhotosError.ImageTooLarge -> Res.string.note_photo_image_too_large
        NotePhotosError.PermissionDenied -> Res.string.note_photo_permission_denied
        NotePhotosError.NotFound -> Res.string.note_photo_not_found
        NotePhotosError.Network -> Res.string.note_photo_network_error
        is NotePhotosError.Repository -> return message
            ?.takeIf(String::isNotBlank)
            ?: stringResource(Res.string.note_photo_error_generic)
    }
    return stringResource(resource)
}
