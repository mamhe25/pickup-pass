package com.pickuppass.android.ui.parent.guardians

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.GuardianEntry
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.GuardianRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianRow(
    val uid: String,
    val entry: GuardianEntry,
    val profile: UserProfile?
)

data class ManageGuardiansUiState(
    val isLoading: Boolean = true,
    val studentName: String = "",
    val guardians: List<GuardianRow> = emptyList(),
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    val formSuccess: String? = null,
    val formIsWarning: Boolean = false,
    val listError: String? = null,
    val temporaryGuardiansEnabled: Boolean = true,
    val guardianSchedulesEnabled: Boolean = true
)

@HiltViewModel
class ManageGuardiansViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val guardianRepository: GuardianRepository,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageGuardiansUiState())
    val uiState: StateFlow<ManageGuardiansUiState> = _uiState

    private lateinit var currentStudentId: String

    fun load(studentId: String) {
        currentStudentId = studentId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, listError = null)

            val entitlements = when (val result = schoolRepository.getEntitlements()) {
                is ApiResult.Success -> result.data.features
                is ApiResult.Failure -> emptyMap()
            }
            val studentResult = studentRepository.getStudent(studentId)
            val student = studentResult.getOrNull()
            if (student == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, listError = "Student not found")
                return@launch
            }

            val rows = student.guardians.map { (uid, entry) ->
                val profile = studentRepository.getUserProfile(uid).getOrNull()
                GuardianRow(uid, entry, profile)
            }.sortedByDescending { it.entry.isPrimary }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                studentName = "${student.fullName} · Grade ${student.grade}",
                guardians = rows,
                temporaryGuardiansEnabled = entitlements["temporary_guardians"] != false,
                guardianSchedulesEnabled = entitlements["guardian_pickup_schedules"] != false
            )
        }
    }

    fun addGuardian(lastName: String, firstName: String, middleInitial: String, suffix: String, email: String, relationship: String) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, formError = null, formSuccess = null)

            when (val result = guardianRepository.addGuardian(currentStudentId, lastName, firstName, middleInitial, suffix, email, relationship)) {
                is ApiResult.Success -> {
                    val message: String
                    val isWarning: Boolean
                    when {
                        result.data.status == "linked_existing" -> {
                            message = "Added — this person already had an account."
                            isWarning = false
                        }
                        result.data.emailSent -> {
                            message = "Added! They'll receive an email to set up their account."
                            isWarning = false
                        }
                        else -> {
                            message = "Added, but the invite email couldn't be sent — ask them to use \"Forgot password?\" on the sign-in page with their email."
                            isWarning = true
                        }
                    }
                    _uiState.value = _uiState.value.copy(isSubmitting = false, formSuccess = message, formIsWarning = isWarning)
                    load(currentStudentId)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, formError = result.message)
                }
            }
        }
    }

    fun addTemporaryGuardian(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        relationship: String,
        validDate: String
    ) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, formError = null, formSuccess = null)
            when (val result = guardianRepository.addTemporaryGuardian(
                currentStudentId, lastName, firstName, middleInitial, suffix, email, relationship, validDate
            )) {
                is ApiResult.Success -> {
                    val message = if (result.data.emailSent) {
                        "One-day pickup authorized for $validDate. The guardian can sign in and generate a QR only on that date."
                    } else {
                        "One-day pickup authorized for $validDate, but the invite email could not be sent. Ask the guardian to use Forgot password with the same email."
                    }
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formSuccess = message,
                        formIsWarning = !result.data.emailSent
                    )
                    load(currentStudentId)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, formError = result.message)
                }
            }
        }
    }


    fun updatePickupSchedule(
        guardianUid: String,
        enabled: Boolean,
        pickupDays: List<String>,
        startDate: String,
        endDate: String
    ) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, formError = null, formSuccess = null)
            when (val result = guardianRepository.updateGuardianSchedule(
                currentStudentId, guardianUid, enabled, pickupDays, startDate, endDate
            )) {
                is ApiResult.Success -> {
                    val message = if (enabled) {
                        "Pickup schedule updated. New QR passes will only work on the selected days."
                    } else {
                        "Pickup schedule removed. This guardian can pick up on any allowed school pickup day."
                    }
                    _uiState.value = _uiState.value.copy(isSubmitting = false, formSuccess = message, formIsWarning = false)
                    load(currentStudentId)
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSubmitting = false, formError = result.message)
            }
        }
    }

    fun removeGuardian(guardianUid: String) {
        viewModelScope.launch {
            when (val result = guardianRepository.removeGuardian(currentStudentId, guardianUid)) {
                is ApiResult.Success -> load(currentStudentId)
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(listError = result.message)
                }
            }
        }
    }
}
