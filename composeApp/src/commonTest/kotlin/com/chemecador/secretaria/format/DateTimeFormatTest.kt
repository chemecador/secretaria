package com.chemecador.secretaria.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class DateTimeFormatTest {

    private val dayFirst24h = DateTimeFormat.DayFirst24h
    private val monthFirst12h = DateTimeFormat(monthFirst = true, twelveHourClock = true)

    @Test
    fun formatDate_dayFirstPadsAndKeepsDayMonthYear() {
        assertEquals("08/04/2026", dayFirst24h.formatDate(LocalDate(2026, 4, 8)))
    }

    @Test
    fun formatDate_monthFirstSwapsDayAndMonth() {
        assertEquals("04/08/2026", monthFirst12h.formatDate(LocalDate(2026, 4, 8)))
    }

    @Test
    fun formatTime_twentyFourHourPadsBothParts() {
        assertEquals("09:05", dayFirst24h.formatTime(LocalTime(9, 5)))
        assertEquals("21:05", dayFirst24h.formatTime(LocalTime(21, 5)))
    }

    @Test
    fun formatTime_twelveHourUsesAmPmWithoutPaddingTheHour() {
        assertEquals("9:05 AM", monthFirst12h.formatTime(LocalTime(9, 5)))
        assertEquals("9:05 PM", monthFirst12h.formatTime(LocalTime(21, 5)))
    }

    @Test
    fun formatTime_twelveHourRendersMidnightAndNoonAsTwelve() {
        assertEquals("12:00 AM", monthFirst12h.formatTime(LocalTime(0, 0)))
        assertEquals("12:30 PM", monthFirst12h.formatTime(LocalTime(12, 30)))
    }
}
