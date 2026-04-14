package com.chemecador.secretaria.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FriendsCodeGeneratorTest {

    @Test
    fun currentFriendCodeDateKey_matchesYearAndDayOfYear() {
        val dateKey = currentFriendCodeDateKey(
            instant = Instant.parse("2026-04-14T10:00:00Z"),
        )

        assertEquals("26104", dateKey)
    }

    @Test
    fun buildFriendCode_appendsCounterToDateKey() {
        assertEquals("261043", buildFriendCode(dateKey = "26104", counter = 3))
    }
}
