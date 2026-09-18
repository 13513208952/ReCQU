package io.github.cqusurvive.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLaunchSettingsTest {
    @Test
    fun missingPreferenceDefaultsToTimetable() {
        assertEquals(
            AppLaunchDestination.TIMETABLE,
            AppLaunchDestination.fromStoredValue(null),
        )
    }

    @Test
    fun storedHomePreferenceIsRestored() {
        assertEquals(
            AppLaunchDestination.HOME,
            AppLaunchDestination.fromStoredValue("home"),
        )
    }

    @Test
    fun unknownPreferenceFallsBackToTimetable() {
        assertEquals(
            AppLaunchDestination.TIMETABLE,
            AppLaunchDestination.fromStoredValue("unknown"),
        )
    }
}
