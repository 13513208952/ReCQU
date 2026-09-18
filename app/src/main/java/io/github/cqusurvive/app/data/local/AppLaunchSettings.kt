package io.github.cqusurvive.app.data.local

import android.content.Context

enum class AppLaunchDestination(
    val navigationIndex: Int,
    internal val storedValue: String,
) {
    HOME(navigationIndex = 0, storedValue = "home"),
    TIMETABLE(navigationIndex = 1, storedValue = "timetable"),
    ;

    companion object {
        fun fromStoredValue(value: String?): AppLaunchDestination =
            entries.firstOrNull { it.storedValue == value } ?: TIMETABLE
    }
}

class AppLaunchSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun destination(): AppLaunchDestination =
        AppLaunchDestination.fromStoredValue(preferences.getString(KEY_DESTINATION, null))

    fun setDestination(destination: AppLaunchDestination) {
        preferences.edit().putString(KEY_DESTINATION, destination.storedValue).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_launch_settings"
        const val KEY_DESTINATION = "destination"
    }
}
