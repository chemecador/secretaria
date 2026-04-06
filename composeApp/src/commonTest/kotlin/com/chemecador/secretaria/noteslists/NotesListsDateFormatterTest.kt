package com.chemecador.secretaria.noteslists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class NotesListsDateFormatterTest {

    @Test
    fun formatNotesListDate_returnsDayMonthYear() {
        val formatted = formatNotesListDate(
            instant = Instant.parse("2026-04-08T12:00:00Z"),
            timeZone = TimeZone.UTC,
        )

        assertEquals("08/04/2026", formatted)
    }
}
