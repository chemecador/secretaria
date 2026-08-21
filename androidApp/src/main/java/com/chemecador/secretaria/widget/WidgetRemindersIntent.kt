package com.chemecador.secretaria.widget

import android.content.Context
import android.content.Intent
import com.chemecador.secretaria.MainActivity
import com.chemecador.secretaria.messaging.NotificationOpenRemindersIntent

/**
 * Los intents que el widget lanza contra la app. Reutiliza la accion del aviso de vencimiento en
 * lugar de declarar otra: el destino es el mismo, y `MainActivity` ya la resuelve.
 */
object WidgetRemindersIntent {

    /** Ademas de abrir la pantalla, pide el dialogo de creacion ya abierto. */
    const val EXTRA_CREATE_REMINDER = "createReminder"

    fun openReminders(context: Context, createReminder: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = NotificationOpenRemindersIntent.ACTION_OPEN_REMINDERS
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(NotificationOpenRemindersIntent.EXTRA_OPEN_REMINDERS, true)
            if (createReminder) {
                putExtra(EXTRA_CREATE_REMINDER, true)
            }
        }

    /**
     * Hoy este extra solo lo pone el widget, que siempre escribe un `Boolean` de verdad. Se lee
     * igual de tolerante que [NotificationOpenRemindersIntent.isOpenRemindersRequest] porque
     * comparten intent: si algun dia el payload de FCM lo trajera, llegaria como `String`.
     */
    fun Intent.isCreateReminderRequest(): Boolean {
        if (action != NotificationOpenRemindersIntent.ACTION_OPEN_REMINDERS) return false
        return getBooleanExtra(EXTRA_CREATE_REMINDER, false) ||
            getStringExtra(EXTRA_CREATE_REMINDER)?.toBoolean() == true
    }
}
