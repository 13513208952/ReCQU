package io.github.cqusurvive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityDestinationTest {
    @Test
    fun launcherUsesPreferredTimetableDestination() {
        assertEquals(
            MainActivity.TIMETABLE_DESTINATION,
            resolveLaunchDestination(
                explicitDestination = null,
                isLauncherLaunch = true,
                preferredDestination = MainActivity.TIMETABLE_DESTINATION,
            ),
        )
    }

    @Test
    fun launcherCanBeChangedBackToHome() {
        assertEquals(
            MainActivity.HOME_DESTINATION,
            resolveLaunchDestination(
                explicitDestination = null,
                isLauncherLaunch = true,
                preferredDestination = MainActivity.HOME_DESTINATION,
            ),
        )
    }

    @Test
    fun widgetExplicitTimetableDestinationAlwaysWins() {
        assertEquals(
            MainActivity.TIMETABLE_DESTINATION,
            resolveLaunchDestination(
                explicitDestination = MainActivity.TIMETABLE_DESTINATION,
                isLauncherLaunch = false,
                preferredDestination = MainActivity.HOME_DESTINATION,
            ),
        )
    }

    @Test
    fun nonLauncherIntentWithoutDestinationKeepsPreviousHomeFallback() {
        assertEquals(
            MainActivity.HOME_DESTINATION,
            resolveLaunchDestination(
                explicitDestination = null,
                isLauncherLaunch = false,
                preferredDestination = MainActivity.TIMETABLE_DESTINATION,
            ),
        )
    }
}
