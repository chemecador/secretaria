package com.chemecador.secretaria.reminders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RemindersOrderingTest {

    @Test
    fun moveReminder_movesItemAndRenumbersOrder() {
        val reminders = listOf(reminder("a", order = 0), reminder("b", order = 1), reminder("c", order = 2))

        val moved = reminders.moveReminder(fromIndex = 2, toIndex = 0)

        assertEquals(listOf("c", "a", "b"), moved.map(Reminder::id))
        assertEquals(listOf(0, 1, 2), moved.map(Reminder::order))
    }

    @Test
    fun moveReminder_outOfBoundsOnlyNormalizes() {
        val reminders = listOf(reminder("a", order = 7), reminder("b", order = 9))

        val moved = reminders.moveReminder(fromIndex = 0, toIndex = 5)

        assertEquals(listOf("a", "b"), moved.map(Reminder::id))
        assertEquals(listOf(0, 1), moved.map(Reminder::order))
    }

    @Test
    fun applyReminderOrder_reassignsOrderFollowingTheGivenIds() {
        val reminders = listOf(reminder("a", order = 0), reminder("b", order = 1), reminder("c", order = 2))

        val reordered = reminders.applyReminderOrder(keys("b", "c", "a"))

        assertEquals(listOf("b", "c", "a"), reordered?.map(Reminder::id))
        assertEquals(listOf(0, 1, 2), reordered?.map(Reminder::order))
    }

    @Test
    fun applyReminderOrder_returnsNullWhenSizesDiffer() {
        val reminders = listOf(reminder("a"), reminder("b"))

        assertNull(reminders.applyReminderOrder(keys("a")))
    }

    @Test
    fun applyReminderOrder_returnsNullWhenIdsAreDuplicated() {
        val reminders = listOf(reminder("a"), reminder("b"))

        assertNull(reminders.applyReminderOrder(keys("a", "a")))
    }

    @Test
    fun applyReminderOrder_returnsNullWhenAnIdIsUnknown() {
        val reminders = listOf(reminder("a"), reminder("b"))

        assertNull(reminders.applyReminderOrder(keys("a", "z")))
    }

    private fun reminder(id: String, order: Int = 0): Reminder = Reminder(
        id = id,
        ownerId = OWNER_ID,
        text = id,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        order = order,
    )

    private fun keys(vararg ids: String): List<ReminderKey> =
        ids.map { id -> ReminderKey(OWNER_ID, id) }

    private companion object {
        const val OWNER_ID = "alex"
    }
}
