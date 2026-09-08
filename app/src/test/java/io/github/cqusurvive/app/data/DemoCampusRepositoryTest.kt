package io.github.cqusurvive.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCampusRepositoryTest {
    @Test
    fun snapshotContainsOnlyMarkedDemoData() = runBlocking {
        val snapshot = DemoCampusRepository().loadSnapshot()

        assertTrue(snapshot.isDemo)
        assertTrue(snapshot.meetings.isNotEmpty())
        assertTrue(snapshot.grades.isNotEmpty())
        assertTrue(snapshot.ratings.all { it.sampleSize >= 5 })
        assertEquals("2024****", snapshot.profile.studentIdMasked)
    }
}
