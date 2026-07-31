package com.pickuppass.android.ui.teacher.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One "Grade X" section of the grouped roster view. */
data class GradeGroup(val grade: String, val sections: List<SectionGroup>)

/** One "Section Y" sub-group within a grade, with its students. */
data class SectionGroup(val section: String, val students: List<Student>)

data class TeacherStudentsUiState(
    val isLoading: Boolean = true,
    val allStudents: List<Student> = emptyList(),
    val school: SchoolInfo? = null,
    val error: String? = null,
    val role: UserRole = UserRole.Unknown,
    /** Teacher-only: distinct from a genuinely empty roster — nudges toward asking the admin to assign one. */
    val hasNoAssignedSections: Boolean = false,
    val searchTerm: String = "",
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    // Set right after a successful add, so the UI can navigate straight
    // into registering that student's parent — the natural next step.
    val justCreatedStudentId: String? = null
) {
    /** Real-time name search over the (already role-scoped) roster. */
    val filteredStudents: List<Student>
        get() = if (searchTerm.isBlank()) {
            allStudents
        } else {
            allStudents.filter { it.fullName.contains(searchTerm, ignoreCase = true) }
        }

    /**
     * Grade -> Section -> Students, built from the FILTERED list (not
     * allStudents) — this is what keeps grouping "logical after filtering":
     * a grade/section with no matches simply doesn't appear, rather than
     * showing empty group headers.
     */
    val groupedStudents: List<GradeGroup>
        get() = filteredStudents
            .groupBy { it.grade }
            .toSortedMap(gradeComparator)
            .map { (grade, studentsInGrade) ->
                GradeGroup(
                    grade = grade,
                    sections = studentsInGrade
                        .groupBy { it.section }
                        .toSortedMap()
                        .map { (section, studentsInSection) ->
                            SectionGroup(section, studentsInSection.sortedBy { it.fullName })
                        }
                )
            }

    companion object {
        /**
         * Grade labels are free text ("1", "Grade 10", "K"), so a plain
         * string sort would put "10" before "2". Extract leading digits
         * when present and sort numerically; non-numeric labels (e.g. "K")
         * sort after all numeric grades, alphabetically among themselves.
         */
        private val gradeComparator = Comparator<String> { a, b ->
            val na = a.filter { it.isDigit() }.toIntOrNull()
            val nb = b.filter { it.isDigit() }.toIntOrNull()
            when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}

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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, hasNoAssignedSections = false)

            val session = authRepository.currentSession()
            if (session?.schoolId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }
            schoolId = session.schoolId

            studentRepository.getSchool(session.schoolId).onSuccess { school ->
                _uiState.value = _uiState.value.copy(school = school)
            }

            // School admins see the whole roster, unchanged from before.
            // Teachers are scoped to only their assigned section(s) — a
            // real restriction that didn't exist until now, not just a
            // search/UI convenience.
            val studentsResult = if (session.role == UserRole.Teacher) {
                val sections = teacherRepository.getMyAssignedSections(session.uid).getOrNull().orEmpty()
                if (sections.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        role = session.role,
                        hasNoAssignedSections = true,
                        allStudents = emptyList()
                    )
                    return@launch
                }
                teacherRepository.getStudentsForSections(session.schoolId, sections)
            } else {
                teacherRepository.getSchoolStudents(session.schoolId)
            }

            studentsResult
                .onSuccess { students ->
                    _uiState.value = _uiState.value.copy(isLoading = false, role = session.role, allStudents = students)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load students")
                }
        }
    }

    fun onSearchChange(term: String) {
        _uiState.value = _uiState.value.copy(searchTerm = term)
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
