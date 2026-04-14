package com.chemecador.secretaria.friends

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun currentFriendCodeDateKey(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val localDate = instant.toLocalDateTime(timeZone).date
    val yearLastTwoDigits = localDate.year % 100
    return "$yearLastTwoDigits${localDate.dayOfYear}"
}

internal fun buildFriendCode(
    dateKey: String,
    counter: Long,
): String = "$dateKey$counter"
