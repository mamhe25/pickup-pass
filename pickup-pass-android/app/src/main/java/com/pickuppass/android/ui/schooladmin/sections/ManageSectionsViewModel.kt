package com.pickuppass.android.ui.schooladmin.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.TeacherSection
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
            when (val result = schoolAdminRepository.listTeachers()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, teachers = result.data)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun addSection(uid: String, grade: String, section: String) {
        if (grade.isBlank() || section.isBlank()) return
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
