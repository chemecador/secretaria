package com.chemecador.secretaria.noteslists

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun formatNotesListDate(localDate: LocalDate): String {
    val day = localDate.day.toString().padStart(2, '0')
    val month = (localDate.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month/${localDate.year}"
}

fun formatNotesListDate(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = formatNotesListDate(instant.toLocalDateTime(timeZone).date)
