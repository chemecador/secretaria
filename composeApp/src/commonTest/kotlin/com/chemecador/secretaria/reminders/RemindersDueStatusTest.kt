package com.chemecador.secretaria.reminders

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RemindersDueStatusTest {

    @Test
    fun isOverdue_allDayTodayIsNotOverdue() {
        val due = ReminderDue(LocalDate.parse("2026-08-13"))

        assertFalse(due.isOverdue(NOW, TimeZone.UTC))
    }

    @Test
    fun isOverdue_allDayYesterdayIsOverdue() {
        val due = ReminderDue(LocalDate.parse("2026-08-12"))

        assertTrue(due.isOverdue(NOW, TimeZone.UTC))
    }

    @Test
    fun isOverdue_allDayTomorrowIsNotOverdue() {
        val due = ReminderDue(LocalDate.parse("2026-08-14"))

        assertFalse(due.isOverdue(NOW, TimeZone.UTC))
    }

    @Test
    fun isOverdue_earlierTimeSameDayIsOverdue() {
        val due = ReminderDue(LocalDate.parse("2026-08-13"), LocalTime.parse("09:00"))

        assertTrue(due.isOverdue(NOW, TimeZone.UTC))
    }

    @Test
    fun isOverdue_laterTimeSameDayIsNotOverdue() {
        val due = ReminderDue(LocalDate.parse("2026-08-13"), LocalTime.parse("18:00"))

        assertFalse(due.isOverdue(NOW, TimeZone.UTC))
    }

    @Test
    fun isOverdue_dependsOnTheGivenTimeZone() {
        val due = ReminderDue(LocalDate.parse("2026-08-13"), LocalTime.parse("11:00"))

        assertTrue(due.isOverdue(NOW, TimeZone.of("Europe/Madrid")))
        assertFalse(due.isOverdue(NOW, TimeZone.UTC))
    }

    private companion object {
        /** 2026-08-13 10:00 UTC, es decir 12:00 en Madrid. */
        val NOW: Instant = Instant.parse("2026-08-13T10:00:00Z")
    }
}
