package com.chemecador.secretaria

import androidx.compose.runtime.Composable

/** Escritorio no registra token FCM, asi que no hay permiso que comprobar ni aviso que prometer. */
@Composable
internal actual fun rememberNotificationPermissionController(): NotificationPermissionController? =
    null
