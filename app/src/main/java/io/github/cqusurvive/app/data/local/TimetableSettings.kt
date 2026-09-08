package io.github.cqusurvive.app.data.local

import android.content.Context
import io.github.cqusurvive.app.domain.AcademicTerm
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TimetableSettings(context: Context) {
    private val preferences = context.getSharedPreferences("timetable_settings", Context.MODE_PRIVATE)

    fun firstDay(term: AcademicTerm): LocalDate? {
        val saved = preferences.getString("first_day_${term.id}", null)
        return saved?.takeIf(String::isNotBlank)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: knownFirstDay(term)
    }

    fun setFirstDay(termId: String, date: LocalDate) {
        preferences.edit().putString("first_day_$termId", date.toString()).apply()
    }

    fun applyFirstDay(term: AcademicTerm, today: LocalDate = LocalDate.now()): AcademicTerm {
        val firstDay = firstDay(term) ?: return term
        return term.copy(
            currentWeek = calculateTeachingWeek(firstDay, today),
            firstDay = firstDay,
        )
    }

    private fun knownFirstDay(term: AcademicTerm): LocalDate? = when {
        term.name.contains("2026") && term.name.contains("秋") -> LocalDate.of(2026, 9, 7)
        else -> null
    }
}

internal fun calculateTeachingWeek(firstDay: LocalDate, today: LocalDate): Int {
    val elapsedDays = ChronoUnit.DAYS.between(firstDay, today)
    return if (elapsedDays < 0) 1 else (elapsedDays / 7 + 1).toInt().coerceIn(1, 30)
}
