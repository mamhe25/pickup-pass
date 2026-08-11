package com.pickuppass.android.ui.schooladmin.manualpickup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualPickupUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val selectedStudent: Student? = null,
    val guardians: List<UserProfile> = emptyList(),
    val selectedGuardian: UserProfile? = null,
    val pickupGates: List<PickupGateItem> = emptyList(),
    val selectedPickupGate: PickupGateItem? = null,
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

@HiltViewModel
class ManualPickupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val pickupRepository: PickupRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManualPickupUiState())
    val uiState: StateFlow<ManualPickupUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val schoolId = authRepository.currentSession()?.schoolId
        if (schoolId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "School session could not be verified")
            return@launch
        }

        val studentsResult = teacherRepository.getSchoolStudents(schoolId)
        val gatesResult = pickupRepository.getActivePickupGates()

        if (studentsResult.isFailure) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = studentsResult.exceptionOrNull()?.message ?: "Could not load students")
            return@launch
        }

        when (gatesResult) {
            is ApiResult.Success -> {
                val gates = gatesResult.data
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    students = studentsResult.getOrNull().orEmpty(),
                    pickupGates = gates,
                    selectedPickupGate = if (gates.size == 1) gates.first() else null
                )
            }
            is ApiResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    students = studentsResult.getOrNull().orEmpty(),
                    error = gatesResult.message
                )
            }
        }
    }

    fun selectStudent(student: Student) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(selectedStudent = student, guardians = emptyList(), selectedGuardian = null, success = null, error = null)
        val profiles = student.guardianUids.mapNotNull { uid -> studentRepository.getUserProfile(uid).getOrNull() }
        _uiState.value = _uiState.value.copy(guardians = profiles)
    }

    fun selectGuardian(guardian: UserProfile) { _uiState.value = _uiState.value.copy(selectedGuardian = guardian, success = null, error = null) }
    fun selectPickupGate(gate: PickupGateItem) { _uiState.value = _uiState.value.copy(selectedPickupGate = gate, success = null, error = null) }
    fun setReason(value: String) { _uiState.value = _uiState.value.copy(reason = value.take(500), success = null, error = null) }

    fun submit() {
        val state = _uiState.value
        val student = state.selectedStudent ?: return setError("Select a student")
        val guardian = state.selectedGuardian ?: return setError("Select an authorized guardian")
        if (state.pickupGates.isNotEmpty() && state.selectedPickupGate == null) return setError("Select the pickup gate used for this release")
        if (state.reason.trim().length < 5) return setError("Reason must be at least 5 characters")
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, error = null, success = null)
            when (val result = pickupRepository.manualOverride(student.id, guardian.uid, state.reason, state.selectedPickupGate?.id)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isSubmitting = false, success = "Release approved and recorded", reason = "")
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    private fun setError(message: String) { _uiState.value = _uiState.value.copy(error = message, success = null) }
}
