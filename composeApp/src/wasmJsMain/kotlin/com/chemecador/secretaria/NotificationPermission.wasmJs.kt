package com.chemecador.secretaria

import androidx.compose.runtime.Composable

/** El navegador no registra token FCM, asi que no hay permiso que comprobar. */
@Composable
internal actual fun rememberNotificationPermissionController(): NotificationPermissionController? =
    null
