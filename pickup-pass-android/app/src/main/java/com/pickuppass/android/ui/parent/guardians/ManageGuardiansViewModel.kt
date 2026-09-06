package com.pickuppass.android.ui.parent.guardians

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.GuardianEntry
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.model.primaryGuardianUidCompat
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.GuardianRepository
import com.pickuppass.android.data.repository.SchoolRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.async
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
    val completedAction: String? = null,
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

    private var currentStudentId: String? = null
    private var loadInProgress = false

    fun load(studentId: String) {
        if (studentId.isBlank() || loadInProgress) return
        currentStudentId = studentId
        loadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    listError = null
                )

                val entitlementsDeferred = async {
                    schoolRepository.getEntitlements()
                }
                val studentDeferred = async {
                    studentRepository.getStudent(studentId)
                }
                val profilesDeferred = async {
                    guardianRepository.getGuardianProfiles(studentId)
                }

                val entitlements = when (val result = entitlementsDeferred.await()) {
                    is ApiResult.Success -> result.data.features
                    is ApiResult.Failure -> emptyMap()
                }

                val student = studentDeferred.await().getOrNull()
                if (student == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        listError = "Student not found"
                    )
                    return@launch
                }

                val profileResult = profilesDeferred.await()
                val profileMap = when (profileResult) {
                    is ApiResult.Success -> profileResult.data
                    is ApiResult.Failure -> emptyMap()
                }
                val identityLoadError = when (profileResult) {
                    is ApiResult.Success -> null
                    is ApiResult.Failure ->
                        "Guardian identity details could not be loaded. Pickup permissions are still shown, but names and photos may be unavailable."
                }

                val primaryUid = student.primaryGuardianUidCompat()
                val linkedGuardianUids =
                    (student.guardianUids + student.guardians.keys)
                        .filter { it.isNotBlank() }
                        .distinct()

                val rows = linkedGuardianUids.map { uid ->
                    val storedEntry = student.guardians[uid] ?: GuardianEntry()
                    GuardianRow(
                        uid = uid,
                        entry = storedEntry.copy(isPrimary = uid == primaryUid),
                        profile = profileMap[uid]
                    )
                }.sortedWith(
                    compareByDescending<GuardianRow> {
                        it.entry.isPrimary == true
                    }.thenBy {
                        it.profile?.displayName.orEmpty().lowercase()
                    }
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    studentName = buildString {
                        append(student.fullName)
                        if (student.grade.isNotBlank()) {
                            append(" · Grade ")
                            append(student.grade)
                        }
                        if (student.section.isNotBlank()) {
                            append(" · ")
                            append(student.section)
                        }
                    },
                    guardians = rows,
                    listError = identityLoadError,
                    temporaryGuardiansEnabled =
                        entitlements["temporary_guardians"] != false,
                    guardianSchedulesEnabled =
                        entitlements["guardian_pickup_schedules"] != false
                )
            } finally {
                loadInProgress = false
            }
        }
    }

    fun addGuardian(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        relationship: String
    ) {
        val validation = validateGuardian(
            lastName = lastName,
            firstName = firstName,
            email = email,
            relationship = relationship,
            validDate = null
        )
        if (validation != null) {
            setFormError(validation)
            return
        }

        val studentId = currentStudentId ?: return
        if (!beginSubmission()) return

        viewModelScope.launch {
            when (
                val result = guardianRepository.addGuardian(
                    studentId,
                    lastName.trim(),
                    firstName.trim(),
                    middleInitial.trim(),
                    suffix.trim(),
                    email.trim(),
                    relationship.trim()
                )
            ) {
                is ApiResult.Success -> {
                    val message: String
                    val warning: Boolean
                    when {
                        result.data.status == "linked_existing" -> {
                            message = "Backup guardian added — this person already had an account."
                            warning = false
                        }

                        result.data.emailSent -> {
                            message = "Backup guardian added. They'll receive an email to set up their account."
                            warning = false
                        }

                        else -> {
                            message = "Backup guardian added, but the invite email couldn't be sent. Ask them to use Forgot password with the same email."
                            warning = true
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formSuccess = message,
                        formIsWarning = warning,
                        completedAction = "guardian-added"
                    )
                    load(studentId)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formError = result.message
                    )
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
        val validation = validateGuardian(
            lastName = lastName,
            firstName = firstName,
            email = email,
            relationship = relationship,
            validDate = validDate
        )
        if (validation != null) {
            setFormError(validation)
            return
        }

        val date = runCatching { LocalDate.parse(validDate.trim()) }.getOrNull()
        if (date == null) {
            setFormError("Pickup date must use YYYY-MM-DD")
            return
        }
        if (date.isBefore(LocalDate.now())) {
            setFormError("One-day pickup authorization cannot be created for a past date")
            return
        }

        val studentId = currentStudentId ?: return
        if (!beginSubmission()) return

        viewModelScope.launch {
            when (
                val result = guardianRepository.addTemporaryGuardian(
                    studentId,
                    lastName.trim(),
                    firstName.trim(),
                    middleInitial.trim(),
                    suffix.trim(),
                    email.trim(),
                    relationship.trim(),
                    validDate.trim()
                )
            ) {
                is ApiResult.Success -> {
                    val message = if (result.data.emailSent) {
                        "One-day pickup authorized for ${validDate.trim()}. The guardian can generate a QR only on that date."
                    } else {
                        "One-day pickup authorized for ${validDate.trim()}, but the invite email could not be sent. Ask the guardian to use Forgot password with the same email."
                    }

                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formSuccess = message,
                        formIsWarning = !result.data.emailSent,
                        completedAction = "temporary-added"
                    )
                    load(studentId)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formError = result.message
                    )
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
        if (enabled && pickupDays.isEmpty()) {
            setFormError("Select at least one pickup day")
            return
        }

        val start = parseOptionalDate(startDate)
            ?: if (startDate.isNotBlank()) {
                setFormError("Start date must use YYYY-MM-DD")
                return
            } else null

        val end = parseOptionalDate(endDate)
            ?: if (endDate.isNotBlank()) {
                setFormError("End date must use YYYY-MM-DD")
                return
            } else null

        if (start != null && end != null && start.isAfter(end)) {
            setFormError("End date cannot be before start date")
            return
        }

        val studentId = currentStudentId ?: return
        if (!beginSubmission()) return

        viewModelScope.launch {
            when (
                val result = guardianRepository.updateGuardianSchedule(
                    studentId,
                    guardianUid,
                    enabled,
                    pickupDays.distinct(),
                    startDate.trim(),
                    endDate.trim()
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formSuccess = if (enabled) {
                            "Pickup schedule updated. New passes will work only on the selected days."
                        } else {
                            "Pickup schedule removed. This guardian can pick up on any allowed school pickup day."
                        },
                        formIsWarning = false,
                        completedAction = "schedule-updated"
                    )
                    load(studentId)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formError = result.message
                    )
                }
            }
        }
    }

    fun removeGuardian(guardianUid: String) {
        val studentId = currentStudentId ?: return
        if (!beginSubmission()) return

        viewModelScope.launch {
            when (
                val result = guardianRepository.removeGuardian(
                    studentId,
                    guardianUid
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formSuccess = "Guardian access removed",
                        formIsWarning = false,
                        completedAction = "guardian-removed"
                    )
                    load(studentId)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        formError = result.message
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            formError = null,
            formSuccess = null,
            formIsWarning = false,
            completedAction = null
        )
    }

    private fun beginSubmission(): Boolean {
        if (_uiState.value.isSubmitting) return false
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            formError = null,
            formSuccess = null,
            formIsWarning = false,
            completedAction = null
        )
        return true
    }

    private fun setFormError(message: String) {
        _uiState.value = _uiState.value.copy(
            formError = message,
            formSuccess = null,
            formIsWarning = false
        )
    }

    private fun validateGuardian(
        lastName: String,
        firstName: String,
        email: String,
        relationship: String,
        validDate: String?
    ): String? {
        if (lastName.isBlank()) return "Last name is required"
        if (firstName.isBlank()) return "First name is required"
        if (email.isBlank()) return "Email address is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return "Enter a valid email address"
        }
        if (relationship.isBlank()) return "Relationship is required"
        if (validDate != null && validDate.isBlank()) {
            return "Pickup date is required"
        }
        return null
    }

    private fun parseOptionalDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return runCatching { LocalDate.parse(value.trim()) }.getOrNull()
    }
}
