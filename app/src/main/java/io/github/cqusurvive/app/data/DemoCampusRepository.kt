package io.github.cqusurvive.app.data

import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CampusRepository
import io.github.cqusurvive.app.domain.CampusSnapshot
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.CourseRating
import io.github.cqusurvive.app.domain.CourseTimeSchedule
import io.github.cqusurvive.app.domain.Exam
import io.github.cqusurvive.app.domain.Grade
import io.github.cqusurvive.app.domain.StudentProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

class DemoCampusRepository : CampusRepository {
    override suspend fun loadSnapshot(forceRefresh: Boolean): CampusSnapshot {
        delay(if (forceRefresh) 450 else 120)
        return CampusSnapshot(
            profile = StudentProfile("离线体验用户", "2024****", "重庆大学"),
            term = AcademicTerm("demo-2025-spring", "2024–2025 学年第二学期", 8),
            meetings = listOf(
                meeting("MATH2001", "高等数学（下）", "张老师", "虎溪 D1137", DayOfWeek.MONDAY, 1, 2),
                meeting("CS2003", "数据结构", "李老师", "虎溪 D1231", DayOfWeek.MONDAY, 5, 6),
                meeting("PHYS1002", "大学物理", "王老师", "虎溪 D1334", DayOfWeek.TUESDAY, 3, 4),
                meeting("ENG1004", "大学英语", "陈老师", "虎溪 DYC201", DayOfWeek.WEDNESDAY, 1, 2),
                meeting("CS2004", "计算机组成原理", "周老师", "虎溪 D1133", DayOfWeek.THURSDAY, 5, 6),
                meeting("PE1002", "篮球", "刘老师", "虎溪风雨操场", DayOfWeek.FRIDAY, 8, 9),
            ),
            grades = listOf(
                Grade("程序设计基础", "91", 3.0, "必修", "2024–2025 第一学期"),
                Grade("线性代数", "86", 3.0, "必修", "2024–2025 第一学期"),
                Grade("中国近现代史纲要", "良", 2.0, "必修", "2024–2025 第一学期"),
                Grade("大学英语（1）", "88", 2.0, "必修", "2024–2025 第一学期"),
            ),
            exams = listOf(
                Exam("大学物理", LocalDate.now().plusDays(12), LocalTime.of(9, 0), LocalTime.of(11, 0), "虎溪 D1234", "18"),
                Exam("高等数学（下）", LocalDate.now().plusDays(16), LocalTime.of(14, 30), LocalTime.of(16, 30), "虎溪 D1337", "42"),
            ),
            ratings = listOf(
                CourseRating("高等数学（下）", "张老师", 4.8, 126, "2023–2024"),
                CourseRating("数据结构", "李老师", 4.6, 84, "2023–2024"),
                CourseRating("大学物理", "王老师", 4.5, 203, "2023–2024"),
            ),
            isDemo = true,
        )
    }

    private fun meeting(
        code: String,
        name: String,
        teacher: String,
        location: String,
        day: DayOfWeek,
        startPeriod: Int,
        endPeriod: Int,
    ) = CourseMeeting(
        courseCode = code,
        courseName = name,
        teacher = teacher,
        location = location,
        day = day,
        start = requireNotNull(CourseTimeSchedule.start(startPeriod)),
        end = requireNotNull(CourseTimeSchedule.end(endPeriod)),
        weeks = (1..16).toSet(),
        startPeriod = startPeriod,
        endPeriod = endPeriod,
    )
}
