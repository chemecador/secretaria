package com.chemecador.secretaria.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RemindersWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = RemindersWidget()

    /**
     * Llega al anadir el widget y en cada ciclo de `updatePeriodMillis`.
     *
     * Aqui NO se puede llamar a `goAsync()`: `super.onUpdate` ya lo hace por dentro para aplicar
     * las vistas, y `goAsync()` entrega el `PendingResult` una sola vez y lo deja a nulo. Una
     * segunda llamada devuelve nulo y revienta el proceso al intentar cerrarlo. El refresco va
     * entonces al scope propio de [RemindersWidgetUpdater]; mientras tanto el `goAsync` de Glance
     * mantiene vivo el proceso, y si aun asi muriera, la copia local sigue intacta y el siguiente
     * ciclo lo vuelve a pedir.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RemindersWidgetUpdater.refresh(context)
    }

    /** Sin widgets en pantalla no hay motivo para conservar el texto de los recordatorios. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RemindersWidgetStore.get(context).clear()
    }
}
