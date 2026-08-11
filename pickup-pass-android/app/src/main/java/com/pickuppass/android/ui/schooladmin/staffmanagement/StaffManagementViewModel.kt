package com.pickuppass.android.ui.schooladmin.staffmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffManagementUiState(
    val isLoading: Boolean = true,
    val teachers: List<TeacherWithSections> = emptyList(),
    val busyUid: String? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class StaffManagementViewModel @Inject constructor(private val repository: SchoolAdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffManagementUiState())
    val uiState: StateFlow<StaffManagementUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = repository.listTeachers()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, teachers = result.data)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
        }
    }

    fun setActive(teacher: TeacherWithSections, active: Boolean) = perform(teacher.uid) {
        when (val result = repository.setTeacherActive(teacher.uid, active)) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    teachers = _uiState.value.teachers.map { if (it.uid == teacher.uid) it.copy(isActive = active) else it },
                    message = if (active) "Teacher reactivated" else "Teacher deactivated and sessions revoked",
                    error = null
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(error = result.message, message = null)
        }
    }

    fun revokeSessions(teacher: TeacherWithSections) = perform(teacher.uid) {
        when (val result = repository.revokeTeacherSessions(teacher.uid)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(message = "All teacher sessions revoked", error = null)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(error = result.message, message = null)
        }
    }

    private fun perform(uid: String, block: suspend () -> Unit) = viewModelScope.launch {
        if (_uiState.value.busyUid != null) return@launch
        _uiState.value = _uiState.value.copy(busyUid = uid, error = null, message = null)
        block()
        _uiState.value = _uiState.value.copy(busyUid = null)
    }
}
