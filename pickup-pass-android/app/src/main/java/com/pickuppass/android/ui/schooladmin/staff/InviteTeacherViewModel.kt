package com.pickuppass.android.ui.schooladmin.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AcademicPlacementOption
import com.pickuppass.android.data.model.TeacherSection
import com.pickuppass.android.data.model.currentPlacementOptions
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class InviteTeacherUiState(
    val isLoadingPlacements: Boolean = true,
    val isSubmitting: Boolean = false,
    val currentAcademicYearName: String = "",
    val availablePlacements: List<AcademicPlacementOption> = emptyList(),
    val placementError: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val successIsWarning: Boolean = false
)

@HiltViewModel
class InviteTeacherViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteTeacherUiState())
    val uiState: StateFlow<InviteTeacherUiState> = _uiState

    private var placementLoadInProgress = false

    init {
        loadPlacements()
    }

    fun loadPlacements() {
        if (_uiState.value.isSubmitting || placementLoadInProgress) return
        placementLoadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoadingPlacements = true,
                    placementError = null,
                    error = null
                )

                when (val result = schoolAdminRepository.getAcademicStructure()) {
                    is ApiResult.Success -> {
                        val currentYear = result.data.currentAcademicYear
                        val placements = result.data.currentPlacementOptions()

                        _uiState.value = _uiState.value.copy(
                            isLoadingPlacements = false,
                            currentAcademicYearName = currentYear?.name.orEmpty(),
                            availablePlacements = placements,
                            placementError = when {
                                currentYear == null ->
                                    "Set a current academic year before inviting teachers."

                                placements.isEmpty() ->
                                    "Add at least one active grade and section to the current academic year before inviting teachers."

                                else -> null
                            }
                        )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingPlacements = false,
                            availablePlacements = emptyList(),
                            placementError = result.message.ifBlank {
                                "Couldn't load School Year & Sections."
                            }
                        )
                    }
                }
            } finally {
                placementLoadInProgress = false
            }
        }
    }

    fun invite(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        placements: List<AcademicPlacementOption>
    ) {
        if (_uiState.value.isSubmitting) return

        if (lastName.isBlank() || firstName.isBlank() || email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Enter the teacher's last name, first name, and email"
            )
            return
        }

        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            _uiState.value = _uiState.value.copy(
                error = "Enter a valid email address"
            )
            return
        }

        if (_uiState.value.placementError != null) {
            _uiState.value = _uiState.value.copy(
                error = "Complete School Year & Sections setup before inviting a teacher"
            )
            return
        }

        if (placements.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Assign at least one current grade and section"
            )
            return
        }

        val allowedById = _uiState.value.availablePlacements
            .associateBy { it.gradeSectionId }

        val validated = placements
            .mapNotNull { allowedById[it.gradeSectionId] }
            .distinctBy { it.gradeSectionId }

        if (validated.size != placements.distinctBy { it.gradeSectionId }.size) {
            _uiState.value = _uiState.value.copy(
                error = "One or more selected sections are no longer active. Refresh the setup and try again."
            )
            return
        }

        val assignedSections = validated.map {
            TeacherSection(
                grade = it.grade,
                section = it.section
            )
        }

        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            error = null,
            successMessage = null,
            successIsWarning = false
        )

        viewModelScope.launch {
            when (
                val result = schoolAdminRepository.inviteTeacher(
                    email = email.trim(),
                    lastName = lastName.trim(),
                    firstName = firstName.trim(),
                    middleInitial = middleInitial.trim(),
                    suffix = suffix.trim(),
                    assignedSections = assignedSections
                )
            ) {
                is ApiResult.Success -> {
                    val emailSent = result.data.emailSent
                    val sectionText = assignedSections.size.toString() +
                        " section" +
                        (if (assignedSections.size == 1) "" else "s")

                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = if (emailSent) {
                            "Invite sent to " + email.trim() + " with " + sectionText + " assigned."
                        } else {
                            "Account created for " + email.trim() + " with " + sectionText +
                                " assigned, but the invite email couldn't be sent. Ask the teacher to use Forgot password? on the sign-in page."
                        },
                        successIsWarning = !emailSent
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

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null,
            successIsWarning = false
        )
    }
}
