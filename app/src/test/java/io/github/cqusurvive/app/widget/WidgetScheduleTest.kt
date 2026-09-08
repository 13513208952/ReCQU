package io.github.cqusurvive.app.widget

import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.StudentProfile
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetScheduleTest {
    @Test
    fun `shows active meeting as next meeting`() {
        val snapshot = snapshot(
            firstDay = LocalDate.of(2026, 9, 7),
            meetings = listOf(meeting(DayOfWeek.MONDAY, 1, "08:30", "10:10")),
        )

        val schedule = buildWidgetSchedule(snapshot, LocalDateTime.of(2026, 9, 7, 9, 0))

        assertEquals(1, schedule.term.currentWeek)
        assertEquals(1, schedule.today.size)
        assertTrue(requireNotNull(schedule.next).isActive)
    }

    @Test
    fun `today widget excludes courses that already ended`() {
        val snapshot = snapshot(
            firstDay = LocalDate.of(2026, 9, 7),
            meetings = listOf(
                meeting(DayOfWeek.MONDAY, 1, "08:30", "10:10"),
                meeting(DayOfWeek.MONDAY, 1, "13:30", "15:10"),
            ),
        )

        val schedule = buildWidgetSchedule(snapshot, LocalDateTime.of(2026, 9, 7, 11, 0))

        assertEquals(2, schedule.today.size)
        assertEquals(1, schedule.remainingToday.size)
        assertEquals(LocalTime.of(13, 30), schedule.remainingToday.single().start)
    }

    @Test
    fun `finds next meeting across week boundary`() {
        val snapshot = snapshot(
            firstDay = LocalDate.of(2026, 9, 7),
            meetings = listOf(meeting(DayOfWeek.MONDAY, 2, "08:30", "10:10")),
        )

        val schedule = buildWidgetSchedule(snapshot, LocalDateTime.of(2026, 9, 13, 21, 0))

        assertEquals(LocalDate.of(2026, 9, 14), requireNotNull(schedule.next).date)
        assertFalse(schedule.next!!.isActive)
    }

    @Test
    fun `before term starts at week one and skips preterm dates`() {
        val snapshot = snapshot(
            firstDay = LocalDate.of(2026, 9, 7),
            meetings = listOf(meeting(DayOfWeek.MONDAY, 1, "08:30", "10:10")),
        )

        val schedule = buildWidgetSchedule(snapshot, LocalDateTime.of(2026, 9, 1, 9, 0))

        assertTrue(schedule.beforeTerm)
        assertEquals(1, schedule.term.currentWeek)
        assertTrue(schedule.today.isEmpty())
        assertEquals(LocalDate.of(2026, 9, 7), assertNotNull(schedule.next).let { schedule.next!!.date })
    }

    @Test
    fun `recalculates a stale cached teaching week`() {
        val snapshot = snapshot(
            firstDay = LocalDate.of(2026, 9, 7),
            currentWeek = 1,
            meetings = listOf(meeting(DayOfWeek.MONDAY, 17, "08:30", "10:10")),
        )

        val schedule = buildWidgetSchedule(snapshot, LocalDateTime.of(2026, 12, 28, 7, 0))

        assertEquals(17, schedule.term.currentWeek)
        assertNotNull(schedule.next)
    }

    private fun snapshot(
        firstDay: LocalDate?,
        currentWeek: Int = 1,
        meetings: List<CourseMeeting>,
    ) = TimetableSnapshot(
        profile = StudentProfile("测试用户", "2026****", "测试学院"),
        term = AcademicTerm("2026-autumn", "2026秋", currentWeek, firstDay),
        meetings = meetings,
        updatedAtEpochMillis = 1L,
    )

    private fun meeting(
        day: DayOfWeek,
        week: Int,
        start: String,
        end: String,
    ) = CourseMeeting(
        courseCode = "TEST",
        courseName = "测试课程",
        teacher = "测试教师",
        location = "测试教室",
        day = day,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        weeks = setOf(week),
    )
}
