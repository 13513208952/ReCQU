package io.github.cqusurvive.app.data.official

import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.CourseTimeSchedule
import io.github.cqusurvive.app.domain.Exam
import io.github.cqusurvive.app.domain.Grade
import io.github.cqusurvive.app.domain.StudentProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedUser(val profile: StudentProfile, val code: String)

internal object CquResponseParser {
    fun user(raw: String): ParsedUser {
        val json = JSONObject(raw)
        val code = json.getString("code")
        // Deliberately whitelist fields: the server response also exposes a field named "password".
        return ParsedUser(
            profile = StudentProfile(
                displayName = json.optString("name", "校园用户"),
                studentIdMasked = maskCode(code),
                department = json.optString("deptName", "重庆大学"),
            ),
            code = code,
        )
    }

    fun term(raw: String, currentWeek: Int): AcademicTerm {
        val data = JSONObject(raw).getJSONObject("data")
        val year = data.optString("year")
        val season = data.optString("term")
        return AcademicTerm(
            id = data.getString("id"),
            name = "$year 年${season}季学期",
            currentWeek = currentWeek.coerceAtLeast(1),
        )
    }

    fun currentWeek(raw: String): Int {
        val data = JSONObject(raw).optJSONObject("data") ?: return 1
        val keys = data.keys()
        while (keys.hasNext()) {
            data.optString(keys.next()).toIntOrNull()?.let { return it }
        }
        return 1
    }

    fun meetings(raw: String): List<CourseMeeting> {
        val data = timetableItems(raw) ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val day = item.optString("weekDay").toIntOrNull()?.takeIf { it in 1..7 } ?: continue
                val period = parseRange(item.optString("periodFormat")) ?: parseRange(item.optString("period")) ?: continue
                if (period.first !in 1..CourseTimeSchedule.MAX_PERIOD || period.last !in 1..CourseTimeSchedule.MAX_PERIOD) continue
                val weeks = parseWeeks(item.optString("teachingWeekFormat")).ifEmpty { (1..20).toSet() }
                val teachers = item.optJSONArray("classTimetableInstrVOList")
                val teacher = teachers?.let { list ->
                    buildList {
                        for (teacherIndex in 0 until list.length()) {
                            list.optJSONObject(teacherIndex)?.optCleanString("instructorName")?.let(::add)
                        }
                    }.joinToString("、")
                }?.takeIf { it.isNotBlank() }
                    ?: item.optCleanString("instructorName").orEmpty()
                add(
                    CourseMeeting(
                        courseCode = item.optString("courseCode"),
                        courseName = item.optString("courseName", "未命名课程"),
                        teacher = formatInstructor(teacher),
                        location = listOfNotNull(
                            item.optCleanString("roomBuildingCampusName"),
                            item.optCleanString("roomName"),
                        ).distinct().joinToString(" ").ifBlank { "地点待定" },
                        day = DayOfWeek.of(day),
                        start = parseTime(item.optString("startTime"))
                            ?: CourseTimeSchedule.start(period.first)
                            ?: LocalTime.MIDNIGHT,
                        end = parseTime(item.optString("endTime"))
                            ?: CourseTimeSchedule.end(period.last)
                            ?: LocalTime.MIDNIGHT,
                        weeks = weeks,
                        startPeriod = period.first,
                        endPeriod = period.last,
                    ),
                )
            }
        }
    }

    fun timetableItemCount(raw: String): Int? = timetableItems(raw)?.length()

    private fun timetableItems(raw: String): JSONArray? {
        val root = JSONObject(raw)
        return root.optJSONArray("classTimetableVOList")
            ?: root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("classTimetableVOList")
    }

    fun grades(raw: String): List<Grade> {
        val data = JSONObject(raw).optJSONObject("data") ?: return emptyList()
        return buildList {
            val terms = data.keys()
            while (terms.hasNext()) {
                val termName = terms.next()
                val list = data.optJSONObject(termName)?.optJSONArray("stuScoreHomePgVoS") ?: continue
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val courseName = item.optNullableString("courseName") ?: continue
                    val shown = item.optNullableString("effectiveScoreShow")
                        ?: item.optNullableString("scoreShow")
                        ?: "—"
                    add(
                        Grade(
                            courseName = courseName,
                            score = shown,
                            credit = item.optString("courseCredit").toDoubleOrNull() ?: 0.0,
                            nature = item.optString("courseNature"),
                            termName = item.optString("sessionName", termName),
                        ),
                    )
                }
            }
        }
    }

    fun gradeItemCount(raw: String): Int? {
        val data = JSONObject(raw).optJSONObject("data") ?: return null
        var count = 0
        val terms = data.keys()
        while (terms.hasNext()) {
            val term = data.optJSONObject(terms.next()) ?: return null
            val grades = term.optJSONArray("stuScoreHomePgVoS") ?: return null
            count += grades.length()
        }
        return count
    }

    fun officialGpa(raw: String): Double {
        val data = JSONObject(raw).getJSONObject("data")
        val value = data.opt("gpa")?.toString()?.toDoubleOrNull()
            ?: error("绩点响应缺少 gpa")
        require(value.isFinite() && value >= 0.0) { "绩点响应无效" }
        return value
    }

    fun exams(raw: String): List<Exam> {
        val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val date = runCatching { LocalDate.parse(item.getString("examDate").take(10)) }.getOrNull() ?: continue
                val start = parseTime(item.optString("startTime")) ?: continue
                val end = parseTime(item.optString("endTime")) ?: continue
                add(
                    Exam(
                        courseName = item.optString("courseName", "未命名课程"),
                        date = date,
                        start = start,
                        end = end,
                        location = listOf(item.optString("buildingName"), item.optString("roomName"))
                            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "地点待定" },
                        seat = item.optString("seatNum", "—"),
                    ),
                )
            }
        }.sortedWith(compareBy<Exam> { it.date }.thenBy { it.start })
    }

    private fun JSONObject.optNullableString(key: String): String? = optCleanString(key)

    private fun JSONObject.optCleanString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", true) }

    private fun formatInstructor(raw: String): String = raw
        .split(';', '；', ',')
        .map { it.trim().replace(Regex("-?\\d+\\[[^]]*]$"), "") }
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("、")

    private fun maskCode(code: String): String = when {
        code.length <= 4 -> "****"
        code.length <= 8 -> code.take(2) + "****" + code.takeLast(2)
        else -> code.take(4) + "****" + code.takeLast(2)
    }

    private fun parseRange(value: String): IntRange? {
        val numbers = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
        return if (numbers.isEmpty()) null else numbers.first()..numbers.last()
    }

    private fun parseWeeks(value: String): Set<Int> = buildSet {
        Regex("(\\d+)(?:\\s*-\\s*(\\d+))?").findAll(value).forEach { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            if (start in 1..30 && end in start..30) addAll(start..end)
        }
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        LocalTime.parse(if (value.length == 5) value else value.take(5))
    }.getOrNull()

}
