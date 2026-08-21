package com.chemecador.secretaria.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.format_clock
import secretaria.composeapp.generated.resources.format_date_order

/**
 * How numeric dates and clock times are laid out.
 *
 * Resolved from string resources rather than from the platform, because the language the app is
 * showing is the only locale signal Compose resources exposes, and a date rendered in a different
 * order than the surrounding text reads as a bug. `05/03/2026` is not ambiguous to an en-US reader,
 * it is a different day, so this cannot be left as a fixed format.
 */
data class DateTimeFormat(
    val monthFirst: Boolean,
    val twelveHourClock: Boolean,
) {
    companion object {
        /** Matches the Spanish original, and is the fallback when a translation omits the tokens. */
        val DayFirst24h = DateTimeFormat(monthFirst = false, twelveHourClock = false)

        /** Publicos porque el widget de Android resuelve los mismos tokens desde sus recursos. */
        const val MONTH_FIRST_TOKEN = "month_first"
        const val CLOCK_12H_TOKEN = "12h"
    }
}

fun DateTimeFormat.formatDate(date: LocalDate): String {
    val day = date.day.twoDigits()
    val month = (date.month.ordinal + 1).twoDigits()
    return if (monthFirst) {
        "$month/$day/${date.year}"
    } else {
        "$day/$month/${date.year}"
    }
}

fun DateTimeFormat.formatTime(time: LocalTime): String {
    val minute = time.minute.twoDigits()
    if (!twelveHourClock) return "${time.hour.twoDigits()}:$minute"

    val suffix = if (time.hour < 12) "AM" else "PM"
    val hour = when (val hour12 = time.hour % 12) {
        0 -> 12
        else -> hour12
    }
    return "$hour:$minute $suffix"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

val LocalDateTimeFormat = staticCompositionLocalOf { DateTimeFormat.DayFirst24h }

/**
 * Reads the two format tokens from the currently resolved locale. Unknown values fall back to the
 * Spanish original so a half-finished translation degrades instead of breaking.
 */
@Composable
fun rememberDateTimeFormat(): DateTimeFormat {
    val dateOrder = stringResource(Res.string.format_date_order)
    val clock = stringResource(Res.string.format_clock)
    return remember(dateOrder, clock) {
        DateTimeFormat(
            monthFirst = dateOrder == DateTimeFormat.MONTH_FIRST_TOKEN,
            twelveHourClock = clock == DateTimeFormat.CLOCK_12H_TOKEN,
        )
    }
}
