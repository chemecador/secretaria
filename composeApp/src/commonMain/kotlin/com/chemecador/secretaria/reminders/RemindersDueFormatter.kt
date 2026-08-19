package com.chemecador.secretaria.reminders

import androidx.compose.runtime.Composable
import com.chemecador.secretaria.format.LocalDateTimeFormat
import com.chemecador.secretaria.format.formatTime
import kotlinx.datetime.LocalTime

/** Solo la hora: la fecha se formatea con `noteslists.formatNotesListDate(LocalDate)`. */
@Composable
internal fun formatReminderTime(time: LocalTime): String =
    LocalDateTimeFormat.current.formatTime(time)
