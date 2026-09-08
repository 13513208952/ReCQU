package io.github.cqusurvive.app.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseTimeScheduleTest {
    @Test
    fun containsAllThirteenCquPeriods() {
        val expected = listOf(
            "08:30" to "09:15",
            "09:25" to "10:10",
            "10:30" to "11:15",
            "11:25" to "12:10",
            "13:30" to "14:15",
            "14:25" to "15:10",
            "15:20" to "16:05",
            "16:25" to "17:10",
            "17:20" to "18:05",
            "19:00" to "19:45",
            "19:55" to "20:40",
            "20:50" to "21:35",
            "21:45" to "22:30",
        )

        assertEquals(13, CourseTimeSchedule.MAX_PERIOD)
        expected.forEachIndexed { index, (start, end) ->
            assertEquals(LocalTime.parse(start), CourseTimeSchedule.start(index + 1))
            assertEquals(LocalTime.parse(end), CourseTimeSchedule.end(index + 1))
        }
    }
}
