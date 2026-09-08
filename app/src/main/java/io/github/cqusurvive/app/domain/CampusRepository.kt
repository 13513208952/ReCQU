package io.github.cqusurvive.app.domain

/**
 * Stable boundary between UI and CQU service adapters.
 *
 * Implementations may use local demo data, an on-device official-service client,
 * or a user-configured compatible backend. Credentials must never cross this API.
 */
interface CampusRepository {
    suspend fun loadSnapshot(forceRefresh: Boolean = false): CampusSnapshot
}

/** A separately refreshable timetable boundary so unrelated modules cannot block it. */
interface TimetableRepository {
    suspend fun loadTimetable(): TimetableSnapshot
    suspend fun loadSupplementary(timetable: TimetableSnapshot): CampusSnapshot
}

/** Grades refresh independently so an exam or timetable failure cannot discard valid grades. */
interface GradeRepository {
    suspend fun loadGrades(): GradeSnapshot
}
