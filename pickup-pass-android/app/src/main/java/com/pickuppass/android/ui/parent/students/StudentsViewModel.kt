package com.pickuppass.android.ui.parent.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val error: String? = null,
    val parentDisplayName: String = "",
    val school: SchoolInfo? = null
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentsUiState())
    val uiState: StateFlow<StudentsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val session = authRepository.currentSession()
            if (session == null || session.schoolId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }

            // Branding fetch failure shouldn't block the actual student list —
            // it's cosmetic, so just leave `school` null on failure.
            studentRepository.getSchool(session.schoolId).onSuccess { school ->
                _uiState.value = _uiState.value.copy(school = school)
            }

            studentRepository.getMyStudents(session.uid, session.schoolId)
                .onSuccess { students ->
                    _uiState.value = _uiState.value.copy(isLoading = false, students = students)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load your students")
                }
        }
    }

    fun signOut() = authRepository.signOut()
}
