package io.github.cqusurvive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.cqusurvive.app.ui.CampusApp
import io.github.cqusurvive.app.ui.theme.CquSurviveTheme
import io.github.cqusurvive.app.widget.TimetableWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var requestedDestination by mutableIntStateOf(HOME_DESTINATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDestination = intent.destination()
        enableEdgeToEdge()
        setContent {
            CquSurviveTheme { CampusApp(requestedDestination = requestedDestination) }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { TimetableWidgetUpdater.updateAll(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination = intent.destination()
    }

    private fun Intent.destination(): Int =
        getIntExtra(EXTRA_DESTINATION, HOME_DESTINATION).coerceIn(HOME_DESTINATION, MORE_DESTINATION)

    companion object {
        const val EXTRA_DESTINATION = "io.github.cqusurvive.app.extra.DESTINATION"
        const val HOME_DESTINATION = 0
        const val TIMETABLE_DESTINATION = 1
        const val MORE_DESTINATION = 3
    }
}
