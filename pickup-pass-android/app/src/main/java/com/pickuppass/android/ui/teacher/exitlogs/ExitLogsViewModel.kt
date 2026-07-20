package com.pickuppass.android.ui.teacher.exitlogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.ExitLogEntry
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExitLogsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val allLogs: List<ExitLogEntry> = emptyList(),
    val searchTerm: String = "",
    val gradeFilter: String? = null,
    val sectionFilter: String? = null,
    val staffFilter: String? = null,
) {
    val availableGrades: List<String> get() = allLogs.map { it.grade }.filter { it.isNotBlank() }.distinct().sorted()
    val availableSections: List<String> get() = allLogs.map { it.section }.filter { it.isNotBlank() }.distinct().sorted()
    val availableStaff: List<String> get() = allLogs.map { it.staffName }.filter { it.isNotBlank() }.distinct().sorted()

    val filteredLogs: List<ExitLogEntry> get() = allLogs.filter { log ->
        (gradeFilter == null || log.grade == gradeFilter) &&
        (sectionFilter == null || log.section == sectionFilter) &&
        (staffFilter == null || log.staffName == staffFilter) &&
        //(searchTerm.isBlank() || listOf(log.studentName, log.guardianName, log.staffName)
        (searchTerm.isBlank() || listOf(log.studentName)
            .any { it.contains(searchTerm, ignoreCase = true) })
    }
}

@HiltViewModel
class ExitLogsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExitLogsUiState())
    val uiState: StateFlow<ExitLogsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val session = authRepository.currentSession()
            if (session?.schoolId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }

            teacherRepository.getExitLogs(session.schoolId)
                .onSuccess { logs ->
                    _uiState.value = _uiState.value.copy(isLoading = false, allLogs = logs)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load dismissal history")
                }
        }
    }

    fun onSearchChange(term: String) {
        _uiState.value = _uiState.value.copy(searchTerm = term)
    }

    fun onGradeFilterChange(grade: String?) {
        _uiState.value = _uiState.value.copy(gradeFilter = grade)
    }

    fun onSectionFilterChange(section: String?) {
        _uiState.value = _uiState.value.copy(sectionFilter = section)
    }

    fun onStaffFilterChange(staff: String?) {
        _uiState.value = _uiState.value.copy(staffFilter = staff)
    }
}
