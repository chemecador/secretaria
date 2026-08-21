package com.chemecador.secretaria.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback

/** Completar sin abrir la app: es lo que evita el viaje a la app para el caso mas comun. */
internal class CompleteReminderAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val ownerId = parameters[OWNER_ID] ?: return
        val reminderId = parameters[REMINDER_ID] ?: return
        RemindersWidgetData.complete(
            context = context,
            ownerId = ownerId,
            reminderId = reminderId,
            order = parameters[ORDER] ?: 0,
        )
    }

    companion object {
        private val OWNER_ID = ActionParameters.Key<String>("ownerId")
        private val REMINDER_ID = ActionParameters.Key<String>("reminderId")
        private val ORDER = ActionParameters.Key<Int>("order")

        fun parametersFor(item: RemindersWidgetItem): ActionParameters = actionParametersOf(
            OWNER_ID to item.ownerId,
            REMINDER_ID to item.id,
            // Completar conserva el `order`, igual que en `RemindersViewModel`.
            ORDER to item.order,
        )
    }
}

internal class RefreshRemindersAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RemindersWidgetUpdater.refreshNow(context)
    }
}
