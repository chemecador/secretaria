package com.chemecador.secretaria.reminders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RemindersPurgeTest {

    @Test
    fun remindersToPurge_returnsCompletedOlderThanRetention() {
        val reminders = listOf(
            reminder("reciente", completed = true, completedAt = NOW - 29.days),
            reminder("antiguo", completed = true, completedAt = NOW - 31.days),
        )

        assertEquals(listOf("antiguo"), reminders.remindersToPurge(NOW))
    }

    @Test
    fun remindersToPurge_keepsReminderExactlyAtTheThreshold() {
        val reminders = listOf(reminder("limite", completed = true, completedAt = NOW - 30.days))

        assertTrue(reminders.remindersToPurge(NOW).isEmpty())
    }

    @Test
    fun remindersToPurge_ignoresPendingRemindersHoweverOldTheyAre() {
        val reminders = listOf(reminder("pendiente", completedAt = NOW - 400.days))

        assertTrue(reminders.remindersToPurge(NOW).isEmpty())
    }

    @Test
    fun remindersToPurge_ignoresCompletedWithoutCompletedAt() {
        val reminders = listOf(reminder("sin-fecha", completed = true, completedAt = null))

        assertTrue(reminders.remindersToPurge(NOW).isEmpty())
    }

    @Test
    fun remindersToPurge_returnsEmptyForAnEmptyList() {
        assertTrue(emptyList<Reminder>().remindersToPurge(NOW).isEmpty())
    }

    private fun reminder(
        id: String,
        completed: Boolean = false,
        completedAt: Instant? = null,
    ): Reminder = Reminder(
        id = id,
        text = id,
        createdAt = NOW - 500.days,
        completed = completed,
        completedAt = completedAt,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T10:00:00Z")
    }
}
