package io.github.cqusurvive.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import io.github.cqusurvive.app.MainActivity
import io.github.cqusurvive.app.R
import io.github.cqusurvive.app.data.local.FileTimetableCache
import io.github.cqusurvive.app.data.local.TimetableSettings
import io.github.cqusurvive.app.domain.CourseMeeting
import io.github.cqusurvive.app.domain.TimetableSnapshot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class TimetableWidgetKind { NEXT, TODAY }

abstract class TimetableWidgetProvider(
    private val kind: TimetableWidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAsync(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in TIME_CHANGE_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            updateAsync(context, manager, ids)
        }
    }

    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = readCurrentSnapshot(context)
                val now = LocalDateTime.now()
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        renderWidget(context, kind, snapshot, now, manager.getAppWidgetOptions(id)),
                    )
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private val TIME_CHANGE_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        )
    }
}

class NextCourseWidgetProvider : TimetableWidgetProvider(TimetableWidgetKind.NEXT)
class TodayCoursesWidgetProvider : TimetableWidgetProvider(TimetableWidgetKind.TODAY)

object TimetableWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = readCurrentSnapshot(context)
        val now = LocalDateTime.now()
        PROVIDERS.forEach { (provider, kind) ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                manager.updateAppWidget(
                    id,
                    renderWidget(context, kind, snapshot, now, manager.getAppWidgetOptions(id)),
                )
            }
        }
    }

    private val PROVIDERS = listOf(
        NextCourseWidgetProvider::class.java to TimetableWidgetKind.NEXT,
        TodayCoursesWidgetProvider::class.java to TimetableWidgetKind.TODAY,
    )
}

private suspend fun readCurrentSnapshot(context: Context): TimetableSnapshot? =
    FileTimetableCache(context).read()?.let { snapshot ->
        snapshot.copy(term = TimetableSettings(context).applyFirstDay(snapshot.term))
    }

private data class WidgetDisplayOptions(
    val maxTodayRows: Int,
    val showNextMeta: Boolean,
)

private fun widgetDisplayOptions(context: Context, options: Bundle): WidgetDisplayOptions {
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    val fontScale = context.resources.configuration.fontScale
    val maxTodayRows = when {
        fontScale >= 1.45f || minHeight < 95 -> 2
        fontScale >= 1.20f || minHeight < 105 -> 3
        else -> 4
    }
    return WidgetDisplayOptions(
        maxTodayRows = maxTodayRows,
        showNextMeta = fontScale < 1.40f && minHeight >= 100,
    )
}

private fun renderWidget(
    context: Context,
    kind: TimetableWidgetKind,
    snapshot: TimetableSnapshot?,
    now: LocalDateTime,
    appWidgetOptions: Bundle,
): RemoteViews {
    val display = widgetDisplayOptions(context, appWidgetOptions)
    return when (kind) {
        TimetableWidgetKind.NEXT -> renderNextWidget(context, snapshot, now, display)
        TimetableWidgetKind.TODAY -> renderTodayWidget(context, snapshot, now, display)
    }
}

private fun renderNextWidget(
    context: Context,
    snapshot: TimetableSnapshot?,
    now: LocalDateTime,
    display: WidgetDisplayOptions,
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_next_course).apply {
    setOnClickPendingIntent(R.id.widget_root, launchAppIntent(context, 101))
    setViewVisibility(R.id.widget_next_meta, if (display.showNextMeta) View.VISIBLE else View.GONE)
    if (snapshot == null) {
        setTextViewText(R.id.widget_next_time, "—")
        setTextViewText(R.id.widget_next_status, "暂无课表")
        setTextViewText(R.id.widget_next_location, "打开 ReCQU")
        setTextViewText(R.id.widget_next_course_name, "登录并刷新课表")
        setTextViewText(R.id.widget_next_meta, "点击进入课表")
        return@apply
    }

    val schedule = buildWidgetSchedule(snapshot, now)
    val next = schedule.next
    if (next == null) {
        setTextViewText(R.id.widget_next_time, "—")
        setTextViewText(R.id.widget_next_status, "近期无课")
        setTextViewText(R.id.widget_next_location, "课程已结束")
        setTextViewText(R.id.widget_next_course_name, schedule.term.name)
        setTextViewText(R.id.widget_next_meta, "第 ${schedule.term.currentWeek} 教学周")
        return@apply
    }

    val isToday = next.date == now.toLocalDate()
    when {
        next.isActive -> {
            val minutes = Duration.between(now.toLocalTime(), next.meeting.end).toMinutes().coerceAtLeast(1)
            setTextViewText(R.id.widget_next_time, next.meeting.end.format(TIME_FORMAT))
            setTextViewText(R.id.widget_next_status, "$minutes 分钟后下课")
        }
        isToday -> {
            setTextViewText(R.id.widget_next_time, next.meeting.start.format(TIME_FORMAT))
            setTextViewText(R.id.widget_next_status, "今天下一节")
        }
        else -> {
            setTextViewText(R.id.widget_next_time, if (schedule.today.isEmpty()) "今日无课" else "今日结束")
            setTextViewText(R.id.widget_next_status, nextDateAndTime(next, now.toLocalDate()))
        }
    }
    setTextViewText(R.id.widget_next_location, next.meeting.location.ifBlank { "地点待定" })
    setTextViewText(R.id.widget_next_course_name, next.meeting.courseName)
    setTextViewText(
        R.id.widget_next_meta,
        listOf(next.meeting.teacher, "${next.meeting.start.format(TIME_FORMAT)}–${next.meeting.end.format(TIME_FORMAT)}")
            .filter(String::isNotBlank)
            .joinToString(" · "),
    )
}

private fun renderTodayWidget(
    context: Context,
    snapshot: TimetableSnapshot?,
    now: LocalDateTime,
    display: WidgetDisplayOptions,
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_today_courses).apply {
    setOnClickPendingIntent(R.id.widget_root, launchAppIntent(context, 102))
    val rowIds = intArrayOf(
        R.id.widget_course_row_1,
        R.id.widget_course_row_2,
        R.id.widget_course_row_3,
        R.id.widget_course_row_4,
    )
    rowIds.forEach { setViewVisibility(it, View.GONE) }

    if (snapshot == null) {
        setTextViewText(R.id.widget_today_subtitle, "打开 ReCQU 获取课表")
        setViewVisibility(R.id.widget_today_empty, View.VISIBLE)
        setTextViewText(R.id.widget_today_empty, "暂无课表缓存")
        return@apply
    }

    val schedule = buildWidgetSchedule(snapshot, now)
    setTextViewText(
        R.id.widget_today_subtitle,
        "${WEEKDAY_NAMES[now.dayOfWeek.value - 1]} · 第 ${schedule.term.currentWeek} 周",
    )
    val meetings = schedule.remainingToday
    val visibleRowIds = rowIds.take(display.maxTodayRows)
    if (meetings.isEmpty()) {
        setViewVisibility(R.id.widget_today_empty, View.VISIBLE)
        setTextViewText(
            R.id.widget_today_empty,
            when {
                schedule.beforeTerm -> "学期尚未开始"
                schedule.today.isEmpty() -> "今天没有课程"
                else -> "今天的课程已结束"
            },
        )
    } else {
        setViewVisibility(R.id.widget_today_empty, View.GONE)
        meetings.take(visibleRowIds.size).forEachIndexed { index, meeting ->
            setViewVisibility(visibleRowIds[index], View.VISIBLE)
            setTextViewText(visibleRowIds[index], formatMeetingRow(meeting, now))
        }
        if (meetings.size > visibleRowIds.size) {
            val lastIndex = visibleRowIds.lastIndex
            setTextViewText(
                visibleRowIds[lastIndex],
                formatMeetingRow(meetings[lastIndex], now) + " · +${meetings.size - visibleRowIds.size}",
            )
        }
    }
}

private fun launchAppIntent(context: Context, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.TIMETABLE_DESTINATION)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun nextDateAndTime(next: WidgetMeeting, today: LocalDate): String {
    val day = when (next.date) {
        today.plusDays(1) -> "明天"
        else -> "${next.date.monthValue}月${next.date.dayOfMonth}日"
    }
    return "$day ${next.meeting.start.format(TIME_FORMAT)}"
}

private fun formatMeetingRow(meeting: CourseMeeting, now: LocalDateTime): String {
    val marker = if (
        !now.toLocalTime().isBefore(meeting.start) && now.toLocalTime().isBefore(meeting.end)
    ) "▶ " else ""
    val location = meeting.location.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
    return "$marker${meeting.start.format(TIME_FORMAT)}  ${meeting.courseName}$location"
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
