package com.chemecador.secretaria.widget

import com.chemecador.secretaria.reminders.Reminder
import com.chemecador.secretaria.reminders.ReminderDue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RemindersWidgetSnapshotTest {

    @Test
    fun `snapshot survives a round trip through json`() {
        val snapshot = RemindersWidgetSnapshot(
            items = listOf(
                widgetItem(id = "a", dueDate = "2026-08-20", dueTime = "09:30", isShared = true),
                widgetItem(id = "b", dueDate = "2026-08-21"),
                widgetItem(id = "c"),
            ),
            updatedAtEpochMillis = 1_755_000_000_000,
            isSignedIn = true,
        )

        assertEquals(snapshot, RemindersWidgetSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun `a snapshot never loaded is not the same as an empty one`() {
        val neverLoaded = RemindersWidgetSnapshot()
        val loadedEmpty = RemindersWidgetSnapshot(updatedAtEpochMillis = 1_755_000_000_000)

        assertNull(RemindersWidgetSnapshot.fromJson(neverLoaded.toJson())?.updatedAtEpochMillis)
        assertEquals(
            1_755_000_000_000,
            RemindersWidgetSnapshot.fromJson(loadedEmpty.toJson())?.updatedAtEpochMillis,
        )
    }

    @Test
    fun `an unreadable copy is discarded instead of half read`() {
        assertNull(RemindersWidgetSnapshot.fromJson("not json"))
        assertNull(RemindersWidgetSnapshot.fromJson("""{"version":99,"items":[]}"""))
    }

    @Test
    fun `an item without a date has no due`() {
        assertNull(widgetItem(id = "a").due)
    }

    @Test
    fun `an all day item keeps a null time`() {
        val due = widgetItem(id = "a", dueDate = "2026-08-20").due

        assertEquals(LocalDate(2026, 8, 20), due?.date)
        assertNull(due?.time)
    }

    @Test
    fun `a dated item keeps date and time`() {
        val due = widgetItem(id = "a", dueDate = "2026-08-20", dueTime = "09:30").due

        assertEquals(ReminderDue(LocalDate(2026, 8, 20), LocalTime(9, 30)), due)
    }

    @Test
    fun `completed reminders stay out of the widget`() {
        val items = listOf(
            reminder(id = "pending", order = 0),
            reminder(id = "done", order = 1, completed = true),
        ).toWidgetItems()

        assertEquals(listOf("pending"), items.map(RemindersWidgetItem::id))
    }

    @Test
    fun `the widget keeps the manual order of the app`() {
        val items = listOf(
            reminder(id = "third", order = 2),
            reminder(id = "first", order = 0),
            reminder(id = "second", order = 1),
        ).toWidgetItems()

        assertEquals(listOf("first", "second", "third"), items.map(RemindersWidgetItem::id))
    }

    /** El `order` puede empatar entre propietarios distintos: cada contador empieza en 0. */
    @Test
    fun `a tie in order breaks by creation date and then by id`() {
        val items = listOf(
            reminder(id = "b", ownerId = "other", order = 0, createdAtSeconds = 20),
            reminder(id = "a", ownerId = "other", order = 0, createdAtSeconds = 20),
            reminder(id = "older", order = 0, createdAtSeconds = 10),
        ).toWidgetItems()

        assertEquals(listOf("older", "a", "b"), items.map(RemindersWidgetItem::id))
    }

    @Test
    fun `a very long list is capped before crossing the process boundary`() {
        val items = List(MAX_WIDGET_ITEMS + 10) { index ->
            reminder(id = "id$index", order = index)
        }.toWidgetItems()

        assertEquals(MAX_WIDGET_ITEMS, items.size)
        assertTrue(items.none { it.id == "id${MAX_WIDGET_ITEMS}" })
    }

    @Test
    fun `mapping a reminder keeps what the widget paints`() {
        val item = reminder(
            id = "a",
            order = 3,
            due = ReminderDue(LocalDate(2026, 8, 20), LocalTime(9, 30)),
            isShared = true,
        ).toWidgetItem()

        assertEquals("a", item.id)
        assertEquals(3, item.order)
        assertEquals("2026-08-20", item.dueDate)
        assertEquals("09:30", item.dueTime)
        assertTrue(item.isShared)
    }

    private fun widgetItem(
        id: String,
        dueDate: String? = null,
        dueTime: String? = null,
        isShared: Boolean = false,
    ) = RemindersWidgetItem(
        ownerId = OWNER_ID,
        id = id,
        text = "Recordatorio $id",
        order = 0,
        dueDate = dueDate,
        dueTime = dueTime,
        isShared = isShared,
    )

    private fun reminder(
        id: String,
        ownerId: String = OWNER_ID,
        order: Int = 0,
        createdAtSeconds: Long = 0,
        completed: Boolean = false,
        due: ReminderDue? = null,
        isShared: Boolean = false,
    ) = Reminder(
        id = id,
        ownerId = ownerId,
        text = "Recordatorio $id",
        createdAt = Instant.fromEpochSeconds(createdAtSeconds),
        due = due,
        completed = completed,
        order = order,
        isShared = isShared,
    )

    private companion object {
        const val OWNER_ID = "owner"
    }
}
