package io.github.cqusurvive.app.data.official

import io.github.cqusurvive.app.domain.CampusRepository
import io.github.cqusurvive.app.domain.CampusSnapshot
import io.github.cqusurvive.app.domain.GradeRepository
import io.github.cqusurvive.app.domain.GradeSnapshot
import io.github.cqusurvive.app.domain.TimetableRepository
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray

class OfficialCampusRepository(
    private val client: CquWebClient,
) : CampusRepository, TimetableRepository, GradeRepository {
    override suspend fun loadSnapshot(forceRefresh: Boolean): CampusSnapshot {
        val timetable = loadTimetable()
        val grades = loadGrades()
        return loadSupplementary(timetable).copy(
            grades = grades.grades,
            gradesUpdatedAtEpochMillis = grades.updatedAtEpochMillis,
        )
    }

    override suspend fun loadTimetable(): TimetableSnapshot {
        val userRaw = client.fetch("/authserver/simple-user").requireSuccess()
        val user = CquResponseParser.user(userRaw)
        val termRaw = client.fetch("/api/resourceapi/session/cur-active-session").requireSuccess()
        val weekRaw = client.fetch("/api/timetable/time/cur-week").requireSuccess()
        val term = CquResponseParser.term(termRaw, CquResponseParser.currentWeek(weekRaw))
        val sessionId = URLEncoder.encode(term.id, StandardCharsets.UTF_8.toString())
        var meetingsRaw = client.fetch(
            path = "/api/timetable/class/timetable/student/my-table-detail?sessionId=$sessionId",
            method = "POST",
            jsonBody = JSONArray().put(user.code).toString(),
        ).requireSuccess()
        var sourceCount = CquResponseParser.timetableItemCount(meetingsRaw)
            ?: error("课表响应缺少课程列表")
        if (sourceCount == 0) {
            val enrollmentRaw = client.fetch("/api/enrollment/timetable/student").requireSuccess()
            val enrollmentCount = CquResponseParser.timetableItemCount(enrollmentRaw)
            if (enrollmentCount != null && enrollmentCount > 0) {
                meetingsRaw = enrollmentRaw
                sourceCount = enrollmentCount
            }
        }
        val meetings = CquResponseParser.meetings(meetingsRaw)
        check(meetings.size == sourceCount) { "课表数据不完整，已保留上次记录" }

        return TimetableSnapshot(
            profile = user.profile,
            term = term,
            meetings = meetings,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun loadGrades(): GradeSnapshot {
        val userRaw = client.fetch("/authserver/simple-user").requireSuccess()
        val profile = CquResponseParser.user(userRaw).profile
        val raw = client.fetch("/api/sam/score/student/score").requireSuccess()
        val sourceCount = CquResponseParser.gradeItemCount(raw)
            ?: error("成绩响应缺少完整课程列表")
        val grades = CquResponseParser.grades(raw)
        check(grades.size == sourceCount) { "成绩数据不完整，已保留上次记录" }
        val gradesUpdatedAt = System.currentTimeMillis()
        val officialGpa = runCatching {
            client.fetch("/api/sam/score/student/studentGpaRanking")
                .requireSuccess()
                .let(CquResponseParser::officialGpa)
        }.getOrNull()
        return GradeSnapshot(
            profile = profile,
            grades = grades,
            updatedAtEpochMillis = gradesUpdatedAt,
            officialGpa = officialGpa,
            gpaUpdatedAtEpochMillis = officialGpa?.let { System.currentTimeMillis() },
        )
    }

    override suspend fun loadSupplementary(timetable: TimetableSnapshot): CampusSnapshot {
        val userRaw = client.fetch("/authserver/simple-user").requireSuccess()
        val encryptedCode = encryptExamParameter(CquResponseParser.user(userRaw).code)
        val examsRaw = client.fetch(
            "/api/exam/examTask/get-student-exam-tab-list?studentId=" +
                URLEncoder.encode(encryptedCode, StandardCharsets.UTF_8.toString()),
        ).requireSuccess()

        return CampusSnapshot(
            profile = timetable.profile,
            term = timetable.term,
            meetings = timetable.meetings,
            grades = emptyList(),
            exams = CquResponseParser.exams(examsRaw),
            ratings = emptyList(),
            isDemo = false,
            updatedAtEpochMillis = timetable.updatedAtEpochMillis,
            isStale = timetable.isStale,
        )
    }

    private fun WebResponse.requireSuccess(): String {
        check(status in 200..299) { "Official service returned HTTP $status" }
        return body
    }

    private fun encryptExamParameter(value: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("cquisse123456789".toByteArray(), "AES"))
        return cipher.doFinal(value.toByteArray()).joinToString("") { "%02X".format(it) }
    }
}
