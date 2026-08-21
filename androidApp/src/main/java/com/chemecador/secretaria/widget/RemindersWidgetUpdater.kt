package com.chemecador.secretaria.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Unico punto de entrada para que el resto de la app toque el widget, para no repartir
 * `updateAll()` por MainActivity, el servicio de mensajeria y el receiver.
 */
object RemindersWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Dispara y olvida. Si el proceso muere antes de terminar no pasa nada: la copia local sigue
     * siendo la anterior y el siguiente ciclo del widget la vuelve a pedir.
     */
    fun refresh(context: Context) {
        val applicationContext = context.applicationContext
        scope.launch { refreshNow(applicationContext) }
    }

    suspend fun refreshNow(context: Context) {
        if (!hasWidgets(context)) return
        RemindersWidgetData.refresh(context)
        notifyChanged(context)
    }

    /**
     * Sin ningun widget colocado no se consulta nada. Importa: esto se llama al salir de la app y
     * en cada aviso de recordatorio, y `getReminders()` son dos lecturas de Firestore que la
     * inmensa mayoria de usuarios, sin widget, no tiene por que pagar.
     */
    private fun hasWidgets(context: Context): Boolean {
        val provider = ComponentName(context, RemindersWidgetReceiver::class.java)
        return AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(provider)
            ?.isNotEmpty() == true
    }

    /**
     * Repinta aunque no haya sesion de Glance viva: el `StateFlow` solo llega a las sesiones
     * activas, y tras morir el proceso no hay ninguna.
     */
    suspend fun notifyChanged(context: Context) {
        RemindersWidget().updateAll(context)
    }
}
