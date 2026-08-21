package com.chemecador.secretaria.widget

import android.content.Context
import com.chemecador.secretaria.R
import com.chemecador.secretaria.format.DateTimeFormat
import com.chemecador.secretaria.format.formatDate
import com.chemecador.secretaria.format.formatTime
import com.chemecador.secretaria.reminders.ReminderDue

/**
 * El widget resuelve el formato de fecha y hora igual que la app: desde dos tokens de recursos,
 * no desde la plataforma. Los tokens estan duplicados en `androidApp/res` porque el host Android
 * tiene sus propios recursos, como los nombres de los canales de notificacion; los valores tienen
 * que seguir a los de `composeResources`, o el widget pintaria `03/05` donde la app pinta
 * `05/03`, justo al lado y en la misma pantalla.
 */
internal fun Context.widgetDateTimeFormat(): DateTimeFormat = DateTimeFormat(
    monthFirst = getString(R.string.widget_format_date_order) == DateTimeFormat.MONTH_FIRST_TOKEN,
    twelveHourClock = getString(R.string.widget_format_clock) == DateTimeFormat.CLOCK_12H_TOKEN,
)

/** Mismo texto que `reminderDueLabel` en la pantalla: fecha, y hora solo si el aviso la lleva. */
internal fun Context.formatWidgetDue(due: ReminderDue, format: DateTimeFormat): String {
    val date = format.formatDate(due.date)
    return due.time
        ?.let { time -> getString(R.string.widget_reminder_due_with_time, date, format.formatTime(time)) }
        ?: date
}
