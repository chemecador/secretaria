package com.chemecador.secretaria

import androidx.compose.runtime.Composable

/**
 * Permiso de notificaciones del sistema. Se consulta en el momento en que hace falta y no se
 * guarda en estado: el usuario puede apagarlas desde los ajustes sin volver a pasar por la app.
 */
internal interface NotificationPermissionController {

    /** `false` cuando el aviso no llegaria: permiso denegado o notificaciones apagadas en ajustes. */
    fun areNotificationsEnabled(): Boolean

    /**
     * Pide el permiso al sistema si todavia se puede pedir y, si ya no se puede, abre los ajustes
     * de la app, que es el unico sitio donde queda reactivarlas.
     */
    fun requestNotifications()
}

/**
 * Nulo donde no hay nada que comprobar. Los avisos de vencimiento son push por FCM y hoy solo
 * Android registra token (el resto usa `NoopFcmTokenRegister`), asi que en los demas targets no
 * tiene sentido pedirle al usuario que active notificaciones que no van a llegar.
 */
@Composable
internal expect fun rememberNotificationPermissionController(): NotificationPermissionController?
