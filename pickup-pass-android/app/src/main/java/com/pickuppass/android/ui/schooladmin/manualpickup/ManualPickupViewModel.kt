package com.pickuppass.android.ui.schooladmin.manualpickup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.PickupRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherRepository
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
    val isLoadingGuardians: Boolean = false,
    val pickupGates: List<PickupGateItem> = emptyList(),
    val selectedPickupGate: PickupGateItem? = null,
    val pickupGatesLoaded: Boolean = false,
    val pickupGateError: String? = null,
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

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = true,
            error = null,
            pickupGateError = null
        )

        val schoolId = authRepository.currentSession()?.schoolId
        if (schoolId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "School session could not be verified"
            )
            return@launch
        }

        val studentsResult = teacherRepository.getSchoolStudents(schoolId)
        if (studentsResult.isFailure) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = studentsResult.exceptionOrNull()?.message ?: "Could not load students"
            )
            return@launch
        }

        val students = studentsResult.getOrNull().orEmpty()
        when (val gatesResult = pickupRepository.getActivePickupGates()) {
            is ApiResult.Success -> {
                val gates = gatesResult.data
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    students = students,
                    pickupGates = gates,
                    pickupGatesLoaded = true,
                    pickupGateError = null,
                    selectedPickupGate = when {
                        gates.size == 1 -> gates.first()
                        else -> current.selectedPickupGate?.takeIf { selected ->
                            gates.any { it.id == selected.id }
                        }
                    }
                )
            }

            is ApiResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    students = students,
                    pickupGates = emptyList(),
                    selectedPickupGate = null,
                    pickupGatesLoaded = false,
                    pickupGateError = gatesResult.message
                )
            }
        }
    }

    fun selectStudent(student: Student) {
        _uiState.value = _uiState.value.copy(
            selectedStudent = student,
            guardians = emptyList(),
            selectedGuardian = null,
            isLoadingGuardians = true,
            reason = "",
            success = null,
            error = null
        )

        viewModelScope.launch {
            val profiles = mutableListOf<UserProfile>()
            for (uid in student.guardianUids.filter { it.isNotBlank() }.distinct()) {
                val profile = studentRepository.getUserProfile(uid).getOrNull()
                if (profile != null && profile.isActive) {
                    profiles += profile
                }
            }

            if (_uiState.value.selectedStudent?.id != student.id) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                guardians = profiles,
                isLoadingGuardians = false
            )
        }
    }

    fun clearStudentSelection() {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            selectedStudent = null,
            guardians = emptyList(),
            selectedGuardian = null,
            isLoadingGuardians = false,
            reason = "",
            success = null,
            error = null
        )
    }

    fun selectGuardian(guardian: UserProfile) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            selectedGuardian = guardian,
            success = null,
            error = null
        )
    }

    fun selectPickupGate(gate: PickupGateItem) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            selectedPickupGate = gate,
            success = null,
            error = null
        )
    }

    fun setReason(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(
            reason = value.take(500),
            success = null,
            error = null
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val student = state.selectedStudent ?: return setError("Select a student")
        val guardian = state.selectedGuardian ?: return setError("Select an authorized guardian")
        if (!state.pickupGatesLoaded) {
            return setError("Pickup gate information is unavailable. Retry before releasing a student.")
        }
        if (state.pickupGates.isNotEmpty() && state.selectedPickupGate == null) {
            return setError("Select the pickup gate used for this release")
        }
        if (state.reason.trim().length < 5) {
            return setError("Reason must be at least 5 characters")
        }

        _uiState.value = state.copy(
            isSubmitting = true,
            error = null,
            success = null
        )

        viewModelScope.launch {
            when (
                val result = pickupRepository.manualOverride(
                    student.id,
                    guardian.uid,
                    state.reason,
                    state.selectedPickupGate?.id
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        success = "Release approved and recorded",
                        reason = ""
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun startAnotherRelease() {
        if (_uiState.value.isSubmitting) return
        val gates = _uiState.value.pickupGates
        _uiState.value = _uiState.value.copy(
            selectedStudent = null,
            guardians = emptyList(),
            selectedGuardian = null,
            isLoadingGuardians = false,
            selectedPickupGate = if (gates.size == 1) gates.first() else null,
            reason = "",
            success = null,
            error = null
        )
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            error = message,
            success = null
        )
    }
}
