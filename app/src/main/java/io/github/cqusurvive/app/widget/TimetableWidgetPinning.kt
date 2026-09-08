package io.github.cqusurvive.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object TimetableWidgetPinning {
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestTodayCourses(context: Context): Boolean =
        request(context, TodayCoursesWidgetProvider::class.java)

    fun requestNextCourse(context: Context): Boolean =
        request(context, NextCourseWidgetProvider::class.java)

    private fun request(
        context: Context,
        provider: Class<out TimetableWidgetProvider>,
    ): Boolean = AppWidgetManager.getInstance(context).requestPinAppWidget(
        ComponentName(context, provider),
        null,
        null,
    )
}
