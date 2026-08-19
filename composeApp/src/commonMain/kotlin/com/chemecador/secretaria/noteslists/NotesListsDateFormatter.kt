package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable
import com.chemecador.secretaria.format.LocalDateTimeFormat
import com.chemecador.secretaria.format.formatDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun formatNotesListDate(localDate: LocalDate): String =
    LocalDateTimeFormat.current.formatDate(localDate)

@Composable
fun formatNotesListDate(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = formatNotesListDate(instant.toLocalDateTime(timeZone).date)
