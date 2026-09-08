package io.github.cqusurvive.app.data.local

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableSettingsTest {
    private val firstDay = LocalDate.of(2026, 9, 7)

    @Test
    fun datesBeforeTermOpenOnFirstWeek() {
        assertEquals(1, calculateTeachingWeek(firstDay, LocalDate.of(2026, 9, 4)))
    }

    @Test
    fun seventeenthWeekOpensOnWeekSeventeen() {
        assertEquals(17, calculateTeachingWeek(firstDay, firstDay.plusWeeks(16)))
        assertEquals(17, calculateTeachingWeek(firstDay, firstDay.plusWeeks(16).plusDays(6)))
    }
}
