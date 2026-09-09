package io.github.cqusurvive.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cqusurvive.app.data.DemoCampusRepository
import io.github.cqusurvive.app.data.local.FileGradeCache
import io.github.cqusurvive.app.data.local.FileTimetableCache
import io.github.cqusurvive.app.data.local.TimetableSettings
import io.github.cqusurvive.app.domain.CampusRepository
import io.github.cqusurvive.app.domain.CampusSnapshot
import io.github.cqusurvive.app.domain.GradeRepository
import io.github.cqusurvive.app.domain.GradeSnapshot
import io.github.cqusurvive.app.domain.StudentProfile
import io.github.cqusurvive.app.domain.TimetableRepository
import io.github.cqusurvive.app.domain.TimetableSnapshot
import io.github.cqusurvive.app.widget.TimetableWidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CampusUiState {
    data object Loading : CampusUiState
    data class Ready(val snapshot: CampusSnapshot, val refreshing: Boolean = false) : CampusUiState
    data class Failed(val message: String) : CampusUiState
}

class CampusViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = DemoCampusRepository()
    private val timetableCache = FileTimetableCache(application)
    private val gradeCache = FileGradeCache(application)
    private val timetableSettings = TimetableSettings(application)
    private var repository: CampusRepository? = null
    private var loadJob: Job? = null
    private var refreshGeneration = 0L
    private val _state = MutableStateFlow<CampusUiState>(CampusUiState.Loading)
    val state: StateFlow<CampusUiState> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        loadJob = viewModelScope.launch {
            val cached = timetableCache.read()
            _state.value = if (cached != null) {
                val currentTerm = timetableSettings.applyFirstDay(cached.term)
                val cachedGrades = gradeCache.read()
                CampusUiState.Ready(
                    cached.copy(term = currentTerm, isStale = true)
                        .toCampusSnapshot()
                        .withCachedGrades(cachedGrades),
                )
            } else {
                CampusUiState.Ready(demoRepository.loadSnapshot())
            }
            TimetableWidgetUpdater.updateAll(getApplication())
        }
    }

    fun refresh() {
        repository?.let(::refreshFrom)
    }

    fun connect(repository: CampusRepository) {
        this.repository = repository
        refreshFrom(repository)
    }

    fun setFirstDay(date: LocalDate) {
        val current = (_state.value as? CampusUiState.Ready)?.snapshot ?: return
        if (current.isDemo) return
        timetableSettings.setFirstDay(current.term.id, date)
        val term = timetableSettings.applyFirstDay(current.term.copy(firstDay = date))
        val updated = current.copy(term = term)
        _state.value = CampusUiState.Ready(updated)
        viewModelScope.launch {
            timetableCache.write(updated.toTimetableSnapshot())
            TimetableWidgetUpdater.updateAll(getApplication())
        }
    }

    fun useDemoData() {
        repository = null
        refreshGeneration++
        _refreshing.value = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            timetableCache.clear()
            gradeCache.clear()
            TimetableWidgetUpdater.updateAll(getApplication())
            _state.value = CampusUiState.Ready(demoRepository.loadSnapshot(forceRefresh = true))
        }
    }

    private fun refreshFrom(repository: CampusRepository) {
        val generation = ++refreshGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val previous = (_state.value as? CampusUiState.Ready)?.snapshot
                if (previous != null) {
                    _state.value = CampusUiState.Ready(previous, refreshing = true)
                } else {
                    _state.value = CampusUiState.Loading
                }

                val timetableRepository = repository as? TimetableRepository
                if (timetableRepository == null) {
                    loadLegacySnapshot(repository, previous)
                    return@launch
                }

                val timetable = runCatching {
                    val fresh = timetableRepository.loadTimetable()
                    val lastSaved = timetableCache.read()
                    check(
                        fresh.meetings.isNotEmpty() ||
                            lastSaved == null ||
                            lastSaved.term.id != fresh.term.id ||
                            lastSaved.meetings.isEmpty(),
                    ) { "空课表不会覆盖同学期的已有记录" }
                    fresh
                }.getOrElse {
                        val cached = timetableCache.read()
                        _state.value = when {
                            cached != null -> {
                                val currentTerm = timetableSettings.applyFirstDay(cached.term)
                                CampusUiState.Ready(
                                    cached.copy(term = currentTerm, isStale = true)
                                        .toCampusSnapshot(previous)
                                        .withCachedGrades(gradeCache.read()),
                                )
                            }
                            previous != null -> CampusUiState.Ready(previous.copy(isStale = !previous.isDemo))
                            else -> CampusUiState.Failed("课表刷新失败，请检查网络或重新登录")
                        }
                        refreshGrades(repository)
                        return@launch
                    }
                    .let { fresh ->
                        val term = timetableSettings.applyFirstDay(fresh.term)
                        fresh.copy(term = term, isStale = false)
                    }

                timetableCache.write(timetable)
                TimetableWidgetUpdater.updateAll(getApplication())
                val scheduleFirst = timetable.toCampusSnapshot(previous)
                    .withCachedGrades(gradeCache.read())
                _state.value = CampusUiState.Ready(scheduleFirst)

                refreshGrades(repository)

                // Exams remain secondary: their failure cannot roll back either a newly
                // validated timetable or the last complete grades snapshot.
                runCatching { timetableRepository.loadSupplementary(timetable) }
                    .onSuccess { supplementary ->
                        val current = (_state.value as? CampusUiState.Ready)?.snapshot ?: scheduleFirst
                        val keepGrades = current.takeIf { it.profile.isSameUser(timetable.profile) }
                        _state.value = CampusUiState.Ready(
                            supplementary.copy(
                                profile = timetable.profile,
                                term = timetable.term,
                                meetings = timetable.meetings,
                                grades = keepGrades?.grades.orEmpty(),
                                updatedAtEpochMillis = timetable.updatedAtEpochMillis,
                                isStale = false,
                                gradesUpdatedAtEpochMillis = keepGrades?.gradesUpdatedAtEpochMillis,
                                gradesStale = keepGrades?.gradesStale ?: false,
                                officialGpa = keepGrades?.officialGpa,
                                gpaUpdatedAtEpochMillis = keepGrades?.gpaUpdatedAtEpochMillis,
                            ),
                        )
                    }
            } finally {
                if (generation == refreshGeneration) _refreshing.value = false
            }
        }
    }

    private suspend fun refreshGrades(repository: CampusRepository) {
        val gradeRepository = repository as? GradeRepository ?: return
        val freshGrades = runCatching {
            val fetched = gradeRepository.loadGrades()
            val lastSaved = gradeCache.read()
            check(
                fetched.grades.isNotEmpty() ||
                    lastSaved == null ||
                    !lastSaved.profile.isSameUser(fetched.profile) ||
                    lastSaved.grades.isEmpty(),
            ) { "空成绩不会覆盖当前用户的已有记录" }
            if (
                fetched.officialGpa == null &&
                lastSaved != null &&
                lastSaved.profile.isSameUser(fetched.profile)
            ) {
                fetched.copy(
                    officialGpa = lastSaved.officialGpa,
                    gpaUpdatedAtEpochMillis = lastSaved.gpaUpdatedAtEpochMillis,
                )
            } else {
                fetched
            }
        }.getOrNull() ?: return

        runCatching { gradeCache.write(freshGrades) }
        val current = (_state.value as? CampusUiState.Ready)?.snapshot ?: return
        if (current.profile.isSameUser(freshGrades.profile)) {
            _state.value = CampusUiState.Ready(
                current.copy(
                    grades = freshGrades.grades,
                    gradesUpdatedAtEpochMillis = freshGrades.updatedAtEpochMillis,
                    gradesStale = false,
                    officialGpa = freshGrades.officialGpa,
                    gpaUpdatedAtEpochMillis = freshGrades.gpaUpdatedAtEpochMillis,
                ),
            )
        }
    }

    private suspend fun loadLegacySnapshot(repository: CampusRepository, previous: CampusSnapshot?) {
        runCatching { repository.loadSnapshot(forceRefresh = true) }
            .onSuccess { _state.value = CampusUiState.Ready(it) }
            .onFailure {
                _state.value = previous?.let(CampusUiState::Ready)
                    ?: CampusUiState.Failed("加载失败")
            }
    }

    private fun TimetableSnapshot.toCampusSnapshot(previous: CampusSnapshot? = null): CampusSnapshot {
        val keepSupplementary = previous?.takeIf {
            !it.isDemo && it.profile.isSameUser(profile)
        }
        return CampusSnapshot(
            profile = profile,
            term = term,
            meetings = meetings,
            grades = keepSupplementary?.grades.orEmpty(),
            exams = keepSupplementary?.exams.orEmpty(),
            ratings = keepSupplementary?.ratings.orEmpty(),
            isDemo = false,
            updatedAtEpochMillis = updatedAtEpochMillis,
            isStale = isStale,
            gradesUpdatedAtEpochMillis = keepSupplementary?.gradesUpdatedAtEpochMillis,
            gradesStale = keepSupplementary?.gradesStale ?: false,
            officialGpa = keepSupplementary?.officialGpa,
            gpaUpdatedAtEpochMillis = keepSupplementary?.gpaUpdatedAtEpochMillis,
        )
    }

    private fun CampusSnapshot.withCachedGrades(cached: GradeSnapshot?): CampusSnapshot {
        if (grades.isNotEmpty() || cached == null || !profile.isSameUser(cached.profile)) return this
        return copy(
            grades = cached.grades,
            gradesUpdatedAtEpochMillis = cached.updatedAtEpochMillis,
            gradesStale = true,
            officialGpa = cached.officialGpa,
            gpaUpdatedAtEpochMillis = cached.gpaUpdatedAtEpochMillis,
        )
    }

    private fun StudentProfile.isSameUser(other: StudentProfile): Boolean =
        studentIdMasked == other.studentIdMasked && displayName == other.displayName

    private fun CampusSnapshot.toTimetableSnapshot() = TimetableSnapshot(
        profile = profile,
        term = term,
        meetings = meetings,
        updatedAtEpochMillis = updatedAtEpochMillis ?: System.currentTimeMillis(),
        isStale = isStale,
    )
}
