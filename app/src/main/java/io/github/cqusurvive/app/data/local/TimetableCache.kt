package io.github.cqusurvive.app.data.local

import android.content.Context
import android.util.AtomicFile
import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.CourseTimeSchedule
import io.github.cqusurvive.app.domain.StudentProfile
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface TimetableCache {
    suspend fun read(): TimetableSnapshot?
    suspend fun write(snapshot: TimetableSnapshot)
    suspend fun clear()
}

class FileTimetableCache(context: Context) : TimetableCache {
    private val file = AtomicFile(context.filesDir.resolve("timetable_snapshot_v1.json"))

    override suspend fun read(): TimetableSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                TimetableJsonCodec.decode(reader.readText())
            }
        }.getOrNull()
    }

    override suspend fun write(snapshot: TimetableSnapshot) = withContext(Dispatchers.IO) {
        TimetableJsonCodec.validate(snapshot)
        val output = file.startWrite()
        try {
            output.write(TimetableJsonCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
            output.flush()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
    }
}

internal object TimetableJsonCodec {
    private const val VERSION = 2

    fun encode(snapshot: TimetableSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("updatedAtEpochMillis", snapshot.updatedAtEpochMillis)
        put("profile", JSONObject().apply {
            put("displayName", snapshot.profile.displayName)
            put("studentIdMasked", snapshot.profile.studentIdMasked)
            put("department", snapshot.profile.department)
        })
        put("term", JSONObject().apply {
            put("id", snapshot.term.id)
            put("name", snapshot.term.name)
            put("currentWeek", snapshot.term.currentWeek)
            snapshot.term.firstDay?.let { put("firstDay", it.toString()) }
        })
        put("meetings", JSONArray().apply {
            snapshot.meetings.forEach { meeting ->
                put(JSONObject().apply {
                    put("courseCode", meeting.courseCode)
                    put("courseName", meeting.courseName)
                    put("teacher", meeting.teacher)
                    put("location", meeting.location)
                    put("day", meeting.day.value)
                    put("start", meeting.start.toString())
                    put("end", meeting.end.toString())
                    meeting.startPeriod?.let { put("startPeriod", it) }
                    meeting.endPeriod?.let { put("endPeriod", it) }
                    put("weeks", JSONArray(meeting.weeks.sorted()))
                })
            }
        })
    }.toString()

    fun decode(raw: String): TimetableSnapshot {
        val root = JSONObject(raw)
        val version = root.getInt("version")
        require(version in 1..VERSION) { "Unsupported timetable cache version" }
        val profile = root.getJSONObject("profile")
        val term = root.getJSONObject("term")
        val meetings = root.getJSONArray("meetings")
        return TimetableSnapshot(
            profile = StudentProfile(
                displayName = profile.getString("displayName"),
                studentIdMasked = profile.getString("studentIdMasked"),
                department = profile.getString("department"),
            ),
            term = AcademicTerm(
                id = term.getString("id"),
                name = term.getString("name"),
                currentWeek = term.getInt("currentWeek"),
                firstDay = term.optString("firstDay").takeIf(String::isNotBlank)
                    ?.let { java.time.LocalDate.parse(it) },
            ),
            meetings = buildList {
                for (index in 0 until meetings.length()) {
                    val item = meetings.getJSONObject(index)
                    val weeks = item.getJSONArray("weeks")
                    val startPeriod = item.optInt("startPeriod").takeIf { it > 0 }
                    val endPeriod = item.optInt("endPeriod").takeIf { it > 0 }
                    add(
                        CourseMeeting(
                            courseCode = item.getString("courseCode"),
                            courseName = item.getString("courseName"),
                            teacher = item.getString("teacher"),
                            location = item.getString("location"),
                            day = DayOfWeek.of(item.getInt("day")),
                            start = if (version == 1) {
                                startPeriod?.let(CourseTimeSchedule::start)
                                    ?: LocalTime.parse(item.getString("start"))
                            } else {
                                LocalTime.parse(item.getString("start"))
                            },
                            end = if (version == 1) {
                                endPeriod?.let(CourseTimeSchedule::end)
                                    ?: LocalTime.parse(item.getString("end"))
                            } else {
                                LocalTime.parse(item.getString("end"))
                            },
                            weeks = buildSet {
                                for (weekIndex in 0 until weeks.length()) add(weeks.getInt(weekIndex))
                            },
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                        ),
                    )
                }
            },
            updatedAtEpochMillis = root.getLong("updatedAtEpochMillis"),
            isStale = false,
        ).also(::validate)
    }

    fun validate(snapshot: TimetableSnapshot) {
        require(snapshot.profile.displayName.isNotBlank()) { "Missing profile name" }
        require(snapshot.term.id.isNotBlank() && snapshot.term.name.isNotBlank()) { "Missing academic term" }
        require(snapshot.term.currentWeek in 1..30) { "Invalid current week" }
        require(snapshot.updatedAtEpochMillis > 0) { "Invalid update time" }
        snapshot.meetings.forEach { meeting ->
            require(meeting.courseName.isNotBlank()) { "Missing course name" }
            require(meeting.start < meeting.end) { "Invalid meeting time" }
            require(meeting.weeks.isNotEmpty() && meeting.weeks.all { it in 1..30 }) { "Invalid teaching weeks" }
        }
    }
}
