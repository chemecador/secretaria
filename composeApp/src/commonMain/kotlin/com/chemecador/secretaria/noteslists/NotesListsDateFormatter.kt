package com.chemecador.secretaria.noteslists

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun formatNotesListDate(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val localDate = instant.toLocalDateTime(timeZone).date
    val day = localDate.day.toString().padStart(2, '0')
    val month = (localDate.month.ordinal + 1).toString().padStart(2, '0')
    return "$day/$month/${localDate.year}"
}
