package com.pickuppass.android.ui.schooladmin.studentlifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AcademicYear
import com.pickuppass.android.data.model.PromotionResponse
import com.pickuppass.android.data.model.StudentLifecycleItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentLifecycleUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val students: List<StudentLifecycleItem> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val filter: String = "all",
    val search: String = "",
    val academicYears: List<AcademicYear> = emptyList(),
    val currentAcademicYearId: String = "",
    val targetAcademicYearId: String = "",
    val promotionPreview: PromotionResponse? = null,
    val error: String? = null,
    val success: String? = null
) {
    val visibleStudents: List<StudentLifecycleItem>
        get() = students.filter { student ->
            (filter == "all" || student.status.equals(filter, ignoreCase = true)) &&
                    (search.isBlank() || student.fullName.contains(search, ignoreCase = true) ||
                            student.studentNumber.contains(search, ignoreCase = true))
        }
}

@HiltViewModel
class StudentLifecycleViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentLifecycleUiState())
    val uiState: StateFlow<StudentLifecycleUiState> = _uiState

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val lifecycle = repository.listStudentLifecycle()
            val structure = repository.getAcademicStructure()

            if (lifecycle is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = lifecycle.message)
                return@launch
            }
            if (structure is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = structure.message)
                return@launch
            }

            val lifeData = (lifecycle as ApiResult.Success).data
            val academic = (structure as ApiResult.Success).data
            val currentId = academic.currentAcademicYear?.id.orEmpty()
            val candidateTarget = academic.academicYears.firstOrNull { it.id != currentId && it.status == "active" }?.id.orEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                students = lifeData.students,
                counts = lifeData.counts,
                academicYears = academic.academicYears,
                currentAcademicYearId = currentId,
                targetAcademicYearId = _uiState.value.targetAcademicYearId.ifBlank { candidateTarget }
            )
        }
    }

    fun setFilter(filter: String) { _uiState.value = _uiState.value.copy(filter = filter) }
    fun setSearch(search: String) { _uiState.value = _uiState.value.copy(search = search) }
    fun selectTargetAcademicYear(id: String) {
        _uiState.value = _uiState.value.copy(targetAcademicYearId = id, promotionPreview = null, error = null, success = null)
    }

    fun updateStatus(studentId: String, status: String, reason: String) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null, success = null)
            when (val result = repository.updateStudentStatus(studentId, status, reason)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isWorking = false, success = "Student status changed to ${status.replaceFirstChar { it.uppercase() }}.")
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isWorking = false, error = result.message)
            }
        }
    }

    fun previewPromotion() {
        val target = _uiState.value.targetAcademicYearId
        if (target.isBlank() || _uiState.value.isWorking) {
            if (target.isBlank()) _uiState.value = _uiState.value.copy(error = "Choose a target school year first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null, success = null, promotionPreview = null)
            when (val result = repository.previewPromotion(target)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isWorking = false, promotionPreview = result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isWorking = false, error = result.message)
            }
        }
    }

    fun executePromotion() {
        val preview = _uiState.value.promotionPreview ?: return
        if (preview.unresolvedCount > 0 || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null, success = null)
            when (val result = repository.executePromotion(preview.targetAcademicYearId)) {
                is ApiResult.Success -> {
                    val body = result.data
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        promotionPreview = body,
                        success = "Promoted ${body.promotedCount} student${if (body.promotedCount == 1) "" else "s"}."
                    )
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isWorking = false, error = result.message)
            }
        }
    }
}
