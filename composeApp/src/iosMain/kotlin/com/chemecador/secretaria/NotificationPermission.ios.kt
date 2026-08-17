package com.chemecador.secretaria

import androidx.compose.runtime.Composable

/**
 * iOS todavia usa `NoopFcmTokenRegister`, asi que el aviso de vencimiento no llega ni con el
 * permiso concedido: avisar aqui solo confundiria. Cuando se registre el token en iOS, este actual
 * es el sitio donde envolver `UNUserNotificationCenter`.
 */
@Composable
internal actual fun rememberNotificationPermissionController(): NotificationPermissionController? =
    null
