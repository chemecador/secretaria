package com.chemecador.secretaria.messaging

import android.content.Intent

/**
 * El aviso de vencimiento no abre un recordatorio concreto, solo la pantalla: no hace falta
 * un modelo compartido como `OpenListRequest`, basta con una bandera.
 */
object NotificationOpenRemindersIntent {
    const val ACTION_OPEN_REMINDERS = "com.chemecador.secretaria.OPEN_REMINDERS"
    const val EXTRA_OPEN_REMINDERS = "openReminders"

    /**
     * El extra llega con dos tipos distintos segun quien pinte la notificacion: `Boolean` cuando
     * la construye [SecretariaMessagingService] en primer plano, y `String` cuando la pinta el
     * sistema en segundo plano, porque ahi los extras salen tal cual del payload `data` de FCM.
     */
    fun Intent.isOpenRemindersRequest(): Boolean {
        if (action != ACTION_OPEN_REMINDERS) return false
        return getBooleanExtra(EXTRA_OPEN_REMINDERS, false) ||
            getStringExtra(EXTRA_OPEN_REMINDERS)?.toBoolean() == true
    }
}
