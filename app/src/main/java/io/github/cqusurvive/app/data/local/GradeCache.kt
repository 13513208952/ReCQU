package io.github.cqusurvive.app.data.local

import android.content.Context
import android.util.AtomicFile
import io.github.cqusurvive.app.domain.Grade
import io.github.cqusurvive.app.domain.GradeSnapshot
import io.github.cqusurvive.app.domain.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface GradeCache {
    suspend fun read(): GradeSnapshot?
    suspend fun write(snapshot: GradeSnapshot)
    suspend fun clear()
}

class FileGradeCache(context: Context) : GradeCache {
    private val file = AtomicFile(context.filesDir.resolve("grade_snapshot_v1.json"))

    override suspend fun read(): GradeSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                GradeJsonCodec.decode(reader.readText())
            }
        }.getOrNull()
    }

    override suspend fun write(snapshot: GradeSnapshot) = withContext(Dispatchers.IO) {
        GradeJsonCodec.validate(snapshot)
        val output = file.startWrite()
        try {
            output.write(GradeJsonCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
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

internal object GradeJsonCodec {
    private const val VERSION = 2

    fun encode(snapshot: GradeSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("updatedAtEpochMillis", snapshot.updatedAtEpochMillis)
        snapshot.officialGpa?.let { put("officialGpa", it) }
        snapshot.gpaUpdatedAtEpochMillis?.let { put("gpaUpdatedAtEpochMillis", it) }
        put("profile", JSONObject().apply {
            put("displayName", snapshot.profile.displayName)
            put("studentIdMasked", snapshot.profile.studentIdMasked)
            put("department", snapshot.profile.department)
        })
        put("grades", JSONArray().apply {
            snapshot.grades.forEach { grade ->
                put(JSONObject().apply {
                    put("courseName", grade.courseName)
                    put("score", grade.score)
                    put("credit", grade.credit)
                    put("nature", grade.nature)
                    put("termName", grade.termName)
                })
            }
        })
    }.toString()

    fun decode(raw: String): GradeSnapshot {
        val root = JSONObject(raw)
        require(root.getInt("version") in 1..VERSION) { "Unsupported grade cache version" }
        val profile = root.getJSONObject("profile")
        val grades = root.getJSONArray("grades")
        return GradeSnapshot(
            profile = StudentProfile(
                displayName = profile.getString("displayName"),
                studentIdMasked = profile.getString("studentIdMasked"),
                department = profile.getString("department"),
            ),
            grades = buildList {
                for (index in 0 until grades.length()) {
                    val item = grades.getJSONObject(index)
                    add(
                        Grade(
                            courseName = item.getString("courseName"),
                            score = item.getString("score"),
                            credit = item.getDouble("credit"),
                            nature = item.getString("nature"),
                            termName = item.getString("termName"),
                        ),
                    )
                }
            },
            updatedAtEpochMillis = root.getLong("updatedAtEpochMillis"),
            officialGpa = root.optDouble("officialGpa", Double.NaN).takeIf(Double::isFinite),
            gpaUpdatedAtEpochMillis = root.optLong("gpaUpdatedAtEpochMillis")
                .takeIf { it > 0 },
        ).also(::validate)
    }

    fun validate(snapshot: GradeSnapshot) {
        require(snapshot.profile.displayName.isNotBlank()) { "Missing profile name" }
        require(snapshot.profile.studentIdMasked.isNotBlank()) { "Missing profile identity" }
        require(snapshot.updatedAtEpochMillis > 0) { "Invalid update time" }
        require((snapshot.officialGpa == null) == (snapshot.gpaUpdatedAtEpochMillis == null)) {
            "Incomplete GPA cache"
        }
        snapshot.officialGpa?.let {
            require(it.isFinite() && it >= 0.0) { "Invalid official GPA" }
            require(snapshot.gpaUpdatedAtEpochMillis != null && snapshot.gpaUpdatedAtEpochMillis > 0) {
                "Missing GPA update time"
            }
        }
        snapshot.grades.forEach { grade ->
            require(grade.courseName.isNotBlank()) { "Missing course name" }
            require(grade.score.isNotBlank()) { "Missing score" }
            require(grade.credit.isFinite() && grade.credit >= 0.0) { "Invalid credit" }
            require(grade.termName.isNotBlank()) { "Missing term name" }
        }
    }
}
