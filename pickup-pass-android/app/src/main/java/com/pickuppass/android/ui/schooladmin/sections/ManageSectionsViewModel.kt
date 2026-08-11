package com.pickuppass.android.ui.schooladmin.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.TeacherSection
import com.pickuppass.android.data.model.GradeSection
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageSectionsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val teachers: List<TeacherWithSections> = emptyList(),
    /** uid -> "Saving…" / "Saved" / error message, transient per-row status shown next to that teacher's chips. */
    val saveStatusByUid: Map<String, String> = emptyMap(),
    val availableSections: List<GradeSection> = emptyList(),
)

@HiltViewModel
class ManageSectionsViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageSectionsUiState())
    val uiState: StateFlow<ManageSectionsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val teachersResult = schoolAdminRepository.listTeachers()
            val structureResult = schoolAdminRepository.getAcademicStructure()
            if (teachersResult is ApiResult.Failure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = teachersResult.message)
                return@launch
            }
            val teachers = (teachersResult as ApiResult.Success).data
            val available = when (structureResult) {
                is ApiResult.Success -> structureResult.data.gradeSections.filter { it.active &&
                    (structureResult.data.currentAcademicYear == null || it.academicYearId == structureResult.data.currentAcademicYear.id) }
                is ApiResult.Failure -> emptyList()
            }
            _uiState.value = _uiState.value.copy(isLoading = false, teachers = teachers, availableSections = available)
        }
    }

    fun addSection(uid: String, grade: String, section: String) {
        if (grade.isBlank() || section.isBlank()) return
        val current = _uiState.value.teachers.firstOrNull { it.uid == uid } ?: return
        if (current.assignedSections.any { it.grade.equals(grade.trim(), true) && it.section.equals(section.trim(), true) }) return
        val updated = _uiState.value.teachers.map { t ->
            if (t.uid == uid) t.copy(assignedSections = t.assignedSections + TeacherSection(grade.trim(), section.trim())) else t
        }
        _uiState.value = _uiState.value.copy(teachers = updated)
        save(uid, updated.first { it.uid == uid }.assignedSections)
    }

    fun removeSection(uid: String, index: Int) {
        val updated = _uiState.value.teachers.map { t ->
            if (t.uid == uid) t.copy(assignedSections = t.assignedSections.filterIndexed { i, _ -> i != index }) else t
        }
        _uiState.value = _uiState.value.copy(teachers = updated)
        save(uid, updated.first { it.uid == uid }.assignedSections)
    }

    private fun save(uid: String, sections: List<TeacherSection>) {
        viewModelScope.launch {
            setStatus(uid, "Saving…")
            when (val result = schoolAdminRepository.updateTeacherSections(uid, sections)) {
                is ApiResult.Success -> setStatus(uid, "Saved")
                is ApiResult.Failure -> setStatus(uid, result.message)
            }
        }
    }

    private fun setStatus(uid: String, status: String) {
        _uiState.value = _uiState.value.copy(
            saveStatusByUid = _uiState.value.saveStatusByUid + (uid to status)
        )
    }
}
