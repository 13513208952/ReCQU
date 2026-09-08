package io.github.cqusurvive.app.domain

import java.time.LocalTime

/** Official CQU class periods used by timetable parsing and presentation. */
object CourseTimeSchedule {
    const val MAX_PERIOD = 13

    private val periods = mapOf(
        1 to (LocalTime.of(8, 30) to LocalTime.of(9, 15)),
        2 to (LocalTime.of(9, 25) to LocalTime.of(10, 10)),
        3 to (LocalTime.of(10, 30) to LocalTime.of(11, 15)),
        4 to (LocalTime.of(11, 25) to LocalTime.of(12, 10)),
        5 to (LocalTime.of(13, 30) to LocalTime.of(14, 15)),
        6 to (LocalTime.of(14, 25) to LocalTime.of(15, 10)),
        7 to (LocalTime.of(15, 20) to LocalTime.of(16, 5)),
        8 to (LocalTime.of(16, 25) to LocalTime.of(17, 10)),
        9 to (LocalTime.of(17, 20) to LocalTime.of(18, 5)),
        10 to (LocalTime.of(19, 0) to LocalTime.of(19, 45)),
        11 to (LocalTime.of(19, 55) to LocalTime.of(20, 40)),
        12 to (LocalTime.of(20, 50) to LocalTime.of(21, 35)),
        13 to (LocalTime.of(21, 45) to LocalTime.of(22, 30)),
    )

    fun times(period: Int): Pair<LocalTime, LocalTime>? = periods[period]
    fun start(period: Int): LocalTime? = periods[period]?.first
    fun end(period: Int): LocalTime? = periods[period]?.second
    fun periodStartingAt(time: LocalTime): Int? = periods.entries.firstOrNull { it.value.first == time }?.key
    fun periodEndingAt(time: LocalTime): Int? = periods.entries.firstOrNull { it.value.second == time }?.key
}
