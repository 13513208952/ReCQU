package io.github.cqusurvive.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class StudentProfile(
    val displayName: String,
    val studentIdMasked: String,
    val department: String,
)

data class AcademicTerm(
    val id: String,
    val name: String,
    val currentWeek: Int,
    val firstDay: LocalDate? = null,
)

data class CourseMeeting(
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val location: String,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val weeks: Set<Int>,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
)

data class Grade(
    val courseName: String,
    val score: String,
    val credit: Double,
    val nature: String,
    val termName: String,
)

data class GradeSnapshot(
    val profile: StudentProfile,
    val grades: List<Grade>,
    val updatedAtEpochMillis: Long,
    val officialGpa: Double? = null,
    val gpaUpdatedAtEpochMillis: Long? = null,
)

data class Exam(
    val courseName: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val location: String,
    val seat: String,
)

data class CourseRating(
    val courseName: String,
    val teacher: String,
    val average: Double,
    val sampleSize: Int,
    val updatedTerm: String,
)

data class TimetableSnapshot(
    val profile: StudentProfile,
    val term: AcademicTerm,
    val meetings: List<CourseMeeting>,
    val updatedAtEpochMillis: Long,
    val isStale: Boolean = false,
)

data class CampusSnapshot(
    val profile: StudentProfile,
    val term: AcademicTerm,
    val meetings: List<CourseMeeting>,
    val grades: List<Grade>,
    val exams: List<Exam>,
    val ratings: List<CourseRating>,
    val isDemo: Boolean,
    val updatedAtEpochMillis: Long? = null,
    val isStale: Boolean = false,
    val gradesUpdatedAtEpochMillis: Long? = null,
    val gradesStale: Boolean = false,
    val officialGpa: Double? = null,
    val gpaUpdatedAtEpochMillis: Long? = null,
)
