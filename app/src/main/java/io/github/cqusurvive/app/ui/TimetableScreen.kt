package io.github.cqusurvive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cqusurvive.app.domain.CampusSnapshot
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.CourseTimeSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
internal fun TimetableScreen(data: CampusSnapshot) {
    var selectedWeek by remember(data.term.id, data.term.currentWeek) {
        mutableIntStateOf(data.term.currentWeek)
    }
    val lastTeachingWeek = maxOf(
        20,
        data.term.currentWeek,
        data.meetings.flatMap { it.weeks }.maxOrNull() ?: 20,
    )

    Column(Modifier.fillMaxSize()) {
        if (data.isStale) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("正在使用上次课表", fontWeight = FontWeight.Bold)
                    Text(
                        data.updatedAtEpochMillis?.let(::formatUpdateTime) ?: "更新时间未知",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = selectedWeek > 1,
                onClick = { selectedWeek-- },
            ) { Text("‹ 上一周") }
            Text("第 $selectedWeek 周", fontWeight = FontWeight.Bold)
            TextButton(
                enabled = selectedWeek < lastTeachingWeek,
                onClick = { selectedWeek++ },
            ) { Text("下一周 ›") }
        }
        if (selectedWeek != data.term.currentWeek) {
            TextButton(
                onClick = { selectedWeek = data.term.currentWeek },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("回到当前第 ${data.term.currentWeek} 周") }
        }

        if (data.meetings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚未获取到完整课表，请保持登录后刷新。")
            }
        } else {
            TimetableWeekGrid(
                meetings = data.meetings.filter { selectedWeek in it.weeks },
                selectedWeek = selectedWeek,
                firstDay = data.term.firstDay,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TimetableWeekGrid(
    meetings: List<CourseMeeting>,
    selectedWeek: Int,
    firstDay: LocalDate?,
    modifier: Modifier = Modifier,
) {
    var selectedMeeting by remember { mutableStateOf<CourseMeeting?>(null) }
    selectedMeeting?.let { meeting ->
        AlertDialog(
            onDismissRequest = { selectedMeeting = null },
            title = { Text(meeting.courseName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${dayLabel(meeting.day)}  ${meeting.start}–${meeting.end}")
                    Text(meeting.location)
                    if (meeting.teacher.isNotBlank()) Text(meeting.teacher)
                    Text(formatWeeks(meeting.weeks))
                    if (meeting.courseCode.isNotBlank()) Text("课程代码：${meeting.courseCode}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMeeting = null }) { Text("关闭") }
            },
        )
    }

    val maxPeriod = max(CourseTimeSchedule.MAX_PERIOD, meetings.maxOfOrNull { it.resolvedEndPeriod() } ?: CourseTimeSchedule.MAX_PERIOD)
    val rowHeight = 58.dp
    val timeColumnWidth = 38.dp
    val headerHeight = 48.dp
    val gridHeight = rowHeight * maxPeriod
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier) {
        val dayWidth = (maxWidth - timeColumnWidth) / 7
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            TimetableHeader(
                dayWidth = dayWidth,
                timeColumnWidth = timeColumnWidth,
                height = headerHeight,
                selectedWeek = selectedWeek,
                firstDay = firstDay,
            )
            Box(Modifier.fillMaxWidth().height(gridHeight)) {
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxSize()) {
                    val left = timeColumnWidth.toPx()
                    val row = rowHeight.toPx()
                    val day = dayWidth.toPx()
                    for (period in 0..maxPeriod) {
                        val y = period * row
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    for (column in 0..7) {
                        val x = left + column * day
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                }

                for (period in 1..maxPeriod) {
                    val times = periodTimes(period)
                    Column(
                        Modifier
                            .offset(y = rowHeight * (period - 1))
                            .width(timeColumnWidth)
                            .height(rowHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("$period", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        times?.let {
                            Text(it.first.toString(), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it.second.toString(), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                meetings.forEach { meeting ->
                    val startPeriod = meeting.resolvedStartPeriod().coerceIn(1, maxPeriod)
                    val endPeriod = meeting.resolvedEndPeriod().coerceIn(startPeriod, maxPeriod)
                    val blockHeight = rowHeight * (endPeriod - startPeriod + 1) - 4.dp
                    val x = timeColumnWidth + dayWidth * (meeting.day.value - 1) + 2.dp
                    val y = rowHeight * (startPeriod - 1) + 2.dp
                    val color = courseColor(meeting.courseCode.ifBlank { meeting.courseName })
                    Column(
                        Modifier
                            .offset(x = x, y = y)
                            .width(dayWidth - 4.dp)
                            .height(blockHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .clickable { selectedMeeting = meeting }
                            .padding(horizontal = 3.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            meeting.courseName,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = max(2, endPeriod - startPeriod + 2),
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            meeting.location,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableHeader(
    dayWidth: Dp,
    timeColumnWidth: Dp,
    height: Dp,
    selectedWeek: Int,
    firstDay: LocalDate?,
) {
    val weekStart = firstDay?.plusWeeks((selectedWeek - 1).toLong())
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.width(timeColumnWidth).height(height), contentAlignment = Alignment.Center) {
            Text("节次", fontSize = 9.sp)
        }
        DayOfWeek.entries.forEach { day ->
            val date = weekStart?.plusDays((day.value - 1).toLong())
            val isToday = date == today
            Column(
                Modifier
                    .width(dayWidth)
                    .height(height)
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(dayLabel(day), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                date?.let { Text(it.format(MONTH_DAY), fontSize = 8.sp) }
            }
        }
    }
}

private fun CourseMeeting.resolvedStartPeriod(): Int =
    startPeriod ?: CourseTimeSchedule.periodStartingAt(start) ?: 1

private fun CourseMeeting.resolvedEndPeriod(): Int =
    endPeriod ?: CourseTimeSchedule.periodEndingAt(end) ?: resolvedStartPeriod()

private fun periodTimes(period: Int) = CourseTimeSchedule.times(period)

private fun dayLabel(day: DayOfWeek): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day.value - 1]

private fun formatWeeks(weeks: Set<Int>): String {
    if (weeks.isEmpty()) return "周次待定"
    val sorted = weeks.sorted()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var previous = start
    for (week in sorted.drop(1)) {
        if (week == previous + 1) previous = week
        else {
            ranges += start..previous
            start = week
            previous = week
        }
    }
    ranges += start..previous
    return "第 " + ranges.joinToString(", ") {
        if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}"
    } + " 周"
}

private fun formatUpdateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun courseColor(key: String): Color = COURSE_COLORS[Math.floorMod(key.hashCode(), COURSE_COLORS.size)]

private val MONTH_DAY = DateTimeFormatter.ofPattern("M/d")
private val COURSE_COLORS = listOf(
    Color(0xFFD8EAFE),
    Color(0xFFE5DCF8),
    Color(0xFFD7F1E1),
    Color(0xFFFFE3D5),
    Color(0xFFFFEDBD),
    Color(0xFFF8DCE7),
    Color(0xFFD9EEF0),
    Color(0xFFE7E4D8),
)
