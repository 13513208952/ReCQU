package io.github.cqusurvive.app.widget

import io.github.cqusurvive.app.data.local.calculateTeachingWeek
import io.github.cqusurvive.app.domain.AcademicTerm
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WidgetMeeting(
    val meeting: CourseMeeting,
    val date: LocalDate,
    val isActive: Boolean,
)

data class WidgetSchedule(
    val term: AcademicTerm,
    val today: List<CourseMeeting>,
    val remainingToday: List<CourseMeeting>,
    val next: WidgetMeeting?,
    val beforeTerm: Boolean,
)

fun buildWidgetSchedule(snapshot: TimetableSnapshot, now: LocalDateTime): WidgetSchedule {
    val term = snapshot.term.forDate(now.toLocalDate())
    val beforeTerm = term.firstDay?.isAfter(now.toLocalDate()) == true
    val today = if (beforeTerm) {
        emptyList()
    } else {
        snapshot.meetings
            .filter { it.day == now.dayOfWeek && term.currentWeek in it.weeks }
            .sortedBy(CourseMeeting::start)
    }
    return WidgetSchedule(
        term = term,
        today = today,
        remainingToday = today.filter { it.end.isAfter(now.toLocalTime()) },
        next = findNextMeeting(snapshot, term, now),
        beforeTerm = beforeTerm,
    )
}

private fun findNextMeeting(
    snapshot: TimetableSnapshot,
    currentTerm: AcademicTerm,
    now: LocalDateTime,
): WidgetMeeting? {
    val firstDay = currentTerm.firstDay
    return (0L..21L).firstNotNullOfOrNull { offset ->
        val date = now.toLocalDate().plusDays(offset)
        if (firstDay != null && date.isBefore(firstDay)) return@firstNotNullOfOrNull null
        val week = teachingWeekOn(currentTerm, now.toLocalDate(), date)
        snapshot.meetings
            .asSequence()
            .filter { it.day == date.dayOfWeek && week in it.weeks }
            .sortedBy(CourseMeeting::start)
            .firstOrNull { meeting ->
                offset > 0 || meeting.end.isAfter(now.toLocalTime())
            }
            ?.let { meeting ->
                WidgetMeeting(
                    meeting = meeting,
                    date = date,
                    isActive = offset == 0L &&
                        !now.toLocalTime().isBefore(meeting.start) &&
                        now.toLocalTime().isBefore(meeting.end),
                )
            }
    }
}

private fun teachingWeekOn(term: AcademicTerm, referenceDate: LocalDate, date: LocalDate): Int {
    term.firstDay?.let { return calculateTeachingWeek(it, date) }
    val referenceMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekOffset = ChronoUnit.WEEKS.between(referenceMonday, dateMonday).toInt()
    return (term.currentWeek + weekOffset).coerceIn(1, 30)
}

private fun AcademicTerm.forDate(date: LocalDate): AcademicTerm =
    firstDay?.let { copy(currentWeek = calculateTeachingWeek(it, date)) } ?: this
