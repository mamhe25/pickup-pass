package com.pickuppass.android.ui.teacher.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeacherStudentsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val school: SchoolInfo? = null,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    // Set right after a successful add, so the UI can navigate straight
    // into registering that student's parent — the natural next step.
    val justCreatedStudentId: String? = null
)

@HiltViewModel
class TeacherStudentsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherStudentsUiState())
    val uiState: StateFlow<TeacherStudentsUiState> = _uiState

    private var schoolId: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val session = authRepository.currentSession()
            if (session?.schoolId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }
            schoolId = session.schoolId

            studentRepository.getSchool(session.schoolId).onSuccess { school ->
                _uiState.value = _uiState.value.copy(school = school)
            }

            teacherRepository.getSchoolStudents(session.schoolId)
                .onSuccess { students ->
                    _uiState.value = _uiState.value.copy(isLoading = false, students = students)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load students")
                }
        }
    }

    fun addStudent(lastName: String, firstName: String, middleInitial: String, suffix: String, grade: String, section: String) {
        if (_uiState.value.isSubmitting) return
        if (lastName.isBlank() || firstName.isBlank()) {
            _uiState.value = _uiState.value.copy(formError = "Enter the student's last name and first name")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, formError = null)

            when (val result = teacherRepository.createStudent(lastName, firstName, middleInitial, suffix, grade, section)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        justCreatedStudentId = result.data
                    )
                    load()
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, formError = result.message)
                }
            }
        }
    }

    fun consumeJustCreatedStudentId() {
        _uiState.value = _uiState.value.copy(justCreatedStudentId = null)
    }
}
