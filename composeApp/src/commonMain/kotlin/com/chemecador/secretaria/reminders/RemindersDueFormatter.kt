package com.chemecador.secretaria.reminders

import kotlinx.datetime.LocalTime

/** Solo la hora: la fecha se formatea con `noteslists.formatNotesListDate(LocalDate)`. */
internal fun formatReminderTime(time: LocalTime): String {
    val hour = time.hour.toString().padStart(2, '0')
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
