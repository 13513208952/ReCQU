package io.github.cqusurvive.app.data.local

import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.StudentProfile
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableJsonCodecTest {
    @Test
    fun roundTripsCompleteTimetable() {
        val original = TimetableSnapshot(
            profile = StudentProfile("测试用户", "2026****01", "测试学院"),
            term = AcademicTerm(
                id = "term-2026-autumn",
                name = "2026 年秋季学期",
                currentWeek = 1,
                firstDay = LocalDate.of(2026, 9, 7),
            ),
            meetings = listOf(
                CourseMeeting(
                    courseCode = "TEST1001",
                    courseName = "测试课程",
                    teacher = "测试教师",
                    location = "测试教室",
                    day = DayOfWeek.MONDAY,
                    start = LocalTime.of(8, 30),
                    end = LocalTime.of(10, 10),
                    weeks = (1..16).toSet(),
                    startPeriod = 1,
                    endPeriod = 2,
                ),
            ),
            updatedAtEpochMillis = 1_788_710_400_000,
        )

        val restored = TimetableJsonCodec.decode(TimetableJsonCodec.encode(original))

        assertEquals(original, restored)
    }
}
