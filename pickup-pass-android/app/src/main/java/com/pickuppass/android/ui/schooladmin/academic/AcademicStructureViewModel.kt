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
import javax.inject.Inject

data class AcademicStructureUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
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

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getAcademicStructure()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    years = result.data.academicYears,
                    sections = result.data.gradeSections,
                    currentYearId = result.data.currentAcademicYear?.id
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun createYear(name: String, startDate: String, endDate: String, current: Boolean) {
        if (name.isBlank() || _uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            when (val result = repository.createAcademicYear(name.trim(), startDate.trim(), endDate.trim(), current)) {
                is ApiResult.Success -> { _uiState.value = _uiState.value.copy(isSaving = false, message = "Academic year created"); load() }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun setCurrentYear(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            when (val result = repository.setCurrentAcademicYear(id)) {
                is ApiResult.Success -> { _uiState.value = _uiState.value.copy(isSaving = false, message = "Current school year updated"); load() }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun createSection(academicYearId: String, grade: String, section: String) {
        if (academicYearId.isBlank() || grade.isBlank() || section.isBlank() || _uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            when (val result = repository.createGradeSection(academicYearId, grade.trim(), section.trim())) {
                is ApiResult.Success -> { _uiState.value = _uiState.value.copy(isSaving = false, message = "Grade/section added"); load() }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun setSectionActive(id: String, active: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setGradeSectionActive(id, active)) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(error = result.message)
            }
        }
    }
}
