package io.github.cqusurvive.app.data.local

import io.github.cqusurvive.app.domain.Grade
import io.github.cqusurvive.app.domain.GradeSnapshot
import io.github.cqusurvive.app.domain.StudentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GradeJsonCodecTest {
    @Test
    fun roundTripsCompleteGrades() {
        val original = GradeSnapshot(
            profile = StudentProfile("测试用户", "2026****01", "测试学院"),
            grades = listOf(
                Grade("测试课程", "优秀", 2.0, "必修", "2026 年秋季学期"),
                Grade("另一课程", "88", 3.5, "选修", "2026 年秋季学期"),
            ),
            updatedAtEpochMillis = 1_788_710_400_000,
            officialGpa = 3.75,
            gpaUpdatedAtEpochMillis = 1_788_710_400_500,
        )

        val restored = GradeJsonCodec.decode(GradeJsonCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun rejectsInvalidCachedGrade() {
        val invalid = GradeSnapshot(
            profile = StudentProfile("测试用户", "2026****01", "测试学院"),
            grades = listOf(Grade("", "88", 2.0, "必修", "2026 年秋季学期")),
            updatedAtEpochMillis = 1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            GradeJsonCodec.validate(invalid)
        }
    }
}
