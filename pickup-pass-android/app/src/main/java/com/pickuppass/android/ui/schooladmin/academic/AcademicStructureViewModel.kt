package com.pickuppass.android.ui.schooladmin.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AcademicYear
import com.pickuppass.android.data.model.GradeSection
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AcademicStructureUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val completedAction: String? = null,
    val years: List<AcademicYear> = emptyList(),
    val sections: List<GradeSection> = emptyList(),
    val currentYearId: String? = null
)

@HiltViewModel
class AcademicStructureViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcademicStructureUiState())
    val uiState: StateFlow<AcademicStructureUiState> = _uiState

    init {
        load()
    }

    fun load() {
        if (_uiState.value.isLoading && _uiState.value.years.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                completedAction = null
            )
            when (val result = repository.getAcademicStructure()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        years = result.data.academicYears,
                        sections = result.data.gradeSections,
                        currentYearId = result.data.currentAcademicYear?.id
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun createYear(
        name: String,
        startDate: String,
        endDate: String,
        current: Boolean
    ) {
        val validation = validateYear(name, startDate, endDate)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(error = validation, message = null)
            return
        }
        runAction(
            completedAction = "year-created",
            successMessage = "Academic year created"
        ) {
            repository.createAcademicYear(
                name.trim(),
                startDate.trim(),
                endDate.trim(),
                current
            )
        }
    }

    fun updateYear(
        id: String,
        name: String,
        startDate: String,
        endDate: String
    ) {
        val validation = validateYear(name, startDate, endDate)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(error = validation, message = null)
            return
        }
        runAction(
            completedAction = "year-updated:$id",
            successMessage = "Academic year updated"
        ) {
            repository.updateAcademicYear(
                id = id,
                name = name.trim(),
                startDate = startDate.trim(),
                endDate = endDate.trim()
            )
        }
    }

    fun setCurrentYear(id: String) {
        runAction(
            completedAction = "year-current:$id",
            successMessage = "Current school year updated"
        ) {
            repository.setCurrentAcademicYear(id)
        }
    }

    fun setYearActive(id: String, active: Boolean) {
        runAction(
            completedAction = "year-status:$id",
            successMessage = if (active) {
                "Academic year reactivated"
            } else {
                "Academic year archived"
            }
        ) {
            repository.setAcademicYearActive(id, active)
        }
    }

    fun deleteYear(id: String) {
        runAction(
            completedAction = "year-deleted:$id",
            successMessage = "Academic year deleted"
        ) {
            repository.deleteAcademicYear(id)
        }
    }

    fun createSection(
        academicYearId: String,
        grade: String,
        section: String
    ) {
        if (academicYearId.isBlank() || grade.isBlank() || section.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Choose an academic year and enter both grade and section",
                message = null
            )
            return
        }
        runAction(
            completedAction = "section-created",
            successMessage = "Grade section added"
        ) {
            repository.createGradeSection(
                academicYearId,
                grade.trim(),
                section.trim()
            )
        }
    }

    fun updateSection(
        id: String,
        grade: String,
        section: String
    ) {
        if (grade.isBlank() || section.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Grade level and section name are required",
                message = null
            )
            return
        }
        runAction(
            completedAction = "section-updated:$id",
            successMessage = "Grade section updated"
        ) {
            repository.updateGradeSection(
                id = id,
                gradeLevel = grade.trim(),
                sectionName = section.trim()
            )
        }
    }

    fun setSectionActive(id: String, active: Boolean) {
        runAction(
            completedAction = "section-status:$id",
            successMessage = if (active) {
                "Grade section reactivated"
            } else {
                "Grade section archived"
            }
        ) {
            repository.setGradeSectionActive(id, active)
        }
    }

    fun deleteSection(id: String) {
        runAction(
            completedAction = "section-deleted:$id",
            successMessage = "Grade section deleted"
        ) {
            repository.deleteGradeSection(id)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            message = null,
            completedAction = null
        )
    }

    private fun runAction(
        completedAction: String,
        successMessage: String,
        block: suspend () -> ApiResult<Unit>
    ) {
        if (_uiState.value.isSaving) return

        // Set synchronously before launching so rapid taps cannot queue the
        // same destructive/update operation twice.
        _uiState.value = _uiState.value.copy(
            isSaving = true,
            error = null,
            message = null,
            completedAction = null
        )

        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> reloadAfterAction(
                    completedAction = completedAction,
                    successMessage = successMessage
                )
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.message
                    )
                }
            }
        }
    }

    private suspend fun reloadAfterAction(
        completedAction: String,
        successMessage: String
    ) {
        when (val result = repository.getAcademicStructure()) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaving = false,
                    years = result.data.academicYears,
                    sections = result.data.gradeSections,
                    currentYearId = result.data.currentAcademicYear?.id,
                    error = null,
                    message = successMessage,
                    completedAction = completedAction
                )
            }
            is ApiResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "$successMessage, but the refreshed list could not be loaded. ${result.message}",
                    message = null,
                    completedAction = completedAction
                )
            }
        }
    }

    private fun validateYear(
        name: String,
        startDate: String,
        endDate: String
    ): String? {
        if (name.isBlank()) return "Academic year name is required"

        val start = startDate.trim()
        val end = endDate.trim()

        val startParsed = try {
            start.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: Exception) {
            return "Start date must use YYYY-MM-DD"
        }

        val endParsed = try {
            end.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: Exception) {
            return "End date must use YYYY-MM-DD"
        }

        if (startParsed != null && endParsed != null && startParsed.isAfter(endParsed)) {
            return "Start date cannot be after end date"
        }

        return null
    }
}
