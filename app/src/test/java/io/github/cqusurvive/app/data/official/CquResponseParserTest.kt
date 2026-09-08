package io.github.cqusurvive.app.data.official

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CquResponseParserTest {
    @Test
    fun userWhitelistsProfileAndMasksCode() {
        val parsed = CquResponseParser.user(
            """{"name":"测试用户","code":"2024123456","deptName":"测试学院","password":"must-not-be-used"}""",
        )

        assertEquals("测试用户", parsed.profile.displayName)
        assertEquals("2024****56", parsed.profile.studentIdMasked)
        assertEquals("2024123456", parsed.code)
    }

    @Test
    fun parsesEnrollmentTimetable() {
        val meetings = CquResponseParser.meetings(
            """{
              "status":"success",
              "data":[{
                "courseCode":"TEST1001",
                "courseName":"测试课程",
                "weekDay":"2",
                "periodFormat":"3-4节",
                "teachingWeekFormat":"1-8周,10-16周",
                "roomBuildingCampusName":"测试校区",
                "roomName":"A101",
                "classTimetableInstrVOList":[{"instructorName":"测试教师"}]
              }]
            }""".trimIndent(),
        )

        assertEquals(1, meetings.size)
        assertEquals(DayOfWeek.TUESDAY, meetings.single().day)
        assertEquals("10:30", meetings.single().start.toString())
        assertEquals("12:10", meetings.single().end.toString())
        assertEquals(((1..8) + (10..16)).toSet(), meetings.single().weeks)
        assertTrue(9 !in meetings.single().weeks)
    }

    @Test
    fun parsesCurrentTermTimetableEnvelopeAndDirectInstructor() {
        val raw = """{
          "classTimetableVOList":[{
            "courseCode":"TEST2002",
            "courseName":"最新学期课程",
            "weekDay":"1",
            "periodFormat":"1-2",
            "teachingWeekFormat":"1-16",
            "roomName":"D1234",
            "instructorName":"测试教师;"
          }]
        }""".trimIndent()

        val meetings = CquResponseParser.meetings(raw)

        assertEquals(1, CquResponseParser.timetableItemCount(raw))
        assertEquals(1, meetings.size)
        assertEquals("测试教师", meetings.single().teacher)
        assertEquals("08:30", meetings.single().start.toString())
    }

    @Test
    fun parsesGradesWithoutDependingOnRealTermNames() {
        val raw = """{
          "data":{"示例学期":{"stuScoreHomePgVoS":[{
            "courseName":"示例课程","effectiveScoreShow":"优秀",
            "courseCredit":"2.0","courseNature":"必修","sessionName":"示例学期"
          }]}}
        }""".trimIndent()
        val grades = CquResponseParser.grades(raw)

        assertEquals(1, CquResponseParser.gradeItemCount(raw))
        assertEquals(1, grades.size)
        assertEquals("优秀", grades.single().score)
        assertEquals(2.0, grades.single().credit, 0.0)
    }

    @Test
    fun parsesOfficialGpaFromStringOrNumber() {
        assertEquals(3.75, CquResponseParser.officialGpa("""{"data":{"gpa":"3.75"}}"""), 0.0)
        assertEquals(4.0, CquResponseParser.officialGpa("""{"data":{"gpa":4}}"""), 0.0)
    }

    @Test
    fun incompleteGradeEnvelopeHasNoCount() {
        assertEquals(null, CquResponseParser.gradeItemCount("""{"data":{"示例学期":{}}}"""))
    }

    @Test
    fun emptyExamArrayIsSupported() {
        assertTrue(CquResponseParser.exams("""{"status":"success","data":[]}""").isEmpty())
    }
}
