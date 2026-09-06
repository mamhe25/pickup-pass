package com.pickuppass.android.ui.teacher.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.TeacherSection
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherOperationsRepository
import com.pickuppass.android.data.repository.TeacherRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeacherStudentsUiState(
    val isLoading: Boolean = true,
    val allStudents: List<Student> = emptyList(),
    val school: SchoolInfo? = null,
    val error: String? = null,
    val placementError: String? = null,
    val role: UserRole = UserRole.Unknown,
    val hasNoAssignedSections: Boolean = false,
    val availableSections: List<TeacherSection> = emptyList(),
    val selectedSectionFilter: TeacherSection? = null,
    val searchTerm: String = "",
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    val justCreatedStudentId: String? = null
) {
    val filteredStudents: List<Student>
        get() {
            val sectionFiltered = selectedSectionFilter?.let { selected ->
                allStudents.filter {
                    it.grade.equals(selected.grade, ignoreCase = true) &&
                        it.section.equals(selected.section, ignoreCase = true)
                }
            } ?: allStudents

            val searched = if (searchTerm.isBlank()) {
                sectionFiltered
            } else {
                sectionFiltered.filter {
                    it.fullName.contains(searchTerm, ignoreCase = true) ||
                        it.studentNumber.contains(searchTerm, ignoreCase = true)
                }
            }

            return searched.sortedWith(
                compareBy<Student>(
                    { gradeSortKey(it.grade) },
                    { it.section.lowercase() },
                    { it.fullName.lowercase() }
                )
            )
        }

    companion object {
        private fun gradeSortKey(grade: String): String {
            val numeric = Regex("\\d+")
                .find(grade)
                ?.value
                ?.toIntOrNull()

            return if (numeric != null) {
                "%05d".format(numeric)
            } else {
                "99999-${grade.lowercase()}"
            }
        }
    }
}

@HiltViewModel
class TeacherStudentsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val teacherOperationsRepository: TeacherOperationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherStudentsUiState())
    val uiState: StateFlow<TeacherStudentsUiState> = _uiState

    private var loadInProgress = false

    init {
        load()
    }

    fun load() {
        if (loadInProgress) return
        loadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    placementError = null,
                    hasNoAssignedSections = false
                )

                val session = authRepository.currentSession()
                if (session?.schoolId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired — please sign in again"
                    )
                    return@launch
                }

                val schoolDeferred = async {
                    studentRepository.getSchool(session.schoolId)
                }

                if (session.role == UserRole.Teacher) {
                    val sectionsResult =
                        teacherRepository.getMyAssignedSections(session.uid)

                    val assignedSections = sectionsResult
                        .getOrElse {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                role = session.role,
                                error = "Couldn't load your assigned sections"
                            )
                            return@launch
                        }
                        .normalizedSections()

                    if (assignedSections.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            role = session.role,
                            school = schoolDeferred.await().getOrNull(),
                            hasNoAssignedSections = true,
                            allStudents = emptyList(),
                            availableSections = emptyList()
                        )
                        return@launch
                    }

                    val studentsResult = teacherRepository.getStudentsForSections(
                        session.schoolId,
                        assignedSections
                    )

                    studentsResult
                        .onSuccess { students ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                role = session.role,
                                school = schoolDeferred.await().getOrNull(),
                                allStudents = students,
                                availableSections = assignedSections,
                                selectedSectionFilter =
                                    _uiState.value.selectedSectionFilter
                                        ?.takeIf { selected ->
                                            assignedSections.any {
                                                it.samePlacement(selected)
                                            }
                                        },
                                error = null
                            )
                        }
                        .onFailure {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                role = session.role,
                                school = schoolDeferred.await().getOrNull(),
                                error = "Couldn't load students"
                            )
                        }

                    return@launch
                }

                val structureDeferred = async {
                    teacherOperationsRepository.getAcademicStructure()
                }
                val studentsDeferred = async {
                    teacherRepository.getSchoolStudents(session.schoolId)
                }

                val structureResult = structureDeferred.await()
                val availableSections = when (structureResult) {
                    is ApiResult.Success -> {
                        val currentYearId =
                            structureResult.data.currentAcademicYear?.id

                        structureResult.data.gradeSections
                            .filter { section ->
                                section.active &&
                                    (
                                        currentYearId.isNullOrBlank() ||
                                            section.academicYearId == currentYearId
                                        )
                            }
                            .map {
                                TeacherSection(
                                    grade = it.gradeLevel,
                                    section = it.sectionName
                                )
                            }
                            .normalizedSections()
                    }

                    is ApiResult.Failure ->
                        emptyList()
                }

                val placementError = when {
                    structureResult is ApiResult.Failure ->
                        "The configured academic structure could not be loaded. Existing roster data is available, but adding a student is disabled."

                    availableSections.isEmpty() ->
                        "No active grade sections are configured for the current academic year. Configure School Year & Sections before adding students."

                    else -> null
                }

                val studentsResult = studentsDeferred.await()
                val school = schoolDeferred.await().getOrNull()

                studentsResult
                    .onSuccess { students ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            role = session.role,
                            school = school,
                            allStudents = students,
                            availableSections = availableSections,
                            selectedSectionFilter =
                                _uiState.value.selectedSectionFilter
                                    ?.takeIf { selected ->
                                        availableSections.any {
                                            it.samePlacement(selected)
                                        }
                                    },
                            placementError = placementError,
                            error = null
                        )
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            role = session.role,
                            school = school,
                            placementError = placementError,
                            error = "Couldn't load students"
                        )
                    }
            } finally {
                loadInProgress = false
            }
        }
    }

    fun onSearchChange(term: String) {
        _uiState.value = _uiState.value.copy(
            searchTerm = term.take(80)
        )
    }

    fun onSectionFilterChange(section: TeacherSection?) {
        _uiState.value = _uiState.value.copy(
            selectedSectionFilter = section
        )
    }

    fun addStudent(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        placement: TeacherSection
    ) {
        if (_uiState.value.isSubmitting) return

        if (lastName.isBlank() || firstName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                formError = "Enter the student's last name and first name"
            )
            return
        }

        val allowedPlacement = _uiState.value.availableSections.any {
            it.samePlacement(placement)
        }
        if (!allowedPlacement) {
            _uiState.value = _uiState.value.copy(
                formError = "Choose a grade and section currently available to your account"
            )
            return
        }

        // Synchronous busy state prevents duplicate student creation on fast taps.
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            formError = null
        )

        viewModelScope.launch {
            when (
                val result = teacherRepository.createStudent(
                    lastName = lastName.trim(),
                    firstName = firstName.trim(),
                    middleInitial = middleInitial.trim(),
                    suffix = suffix.trim(),
                    grade = placement.grade.trim(),
                    section = placement.section.trim()
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        justCreatedStudentId = result.data
                    )
                    load()
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

    fun clearFormFeedback() {
        _uiState.value = _uiState.value.copy(
            formError = null
        )
    }

    fun consumeJustCreatedStudentId() {
        _uiState.value = _uiState.value.copy(
            justCreatedStudentId = null
        )
    }
}

private fun List<TeacherSection>.normalizedSections(): List<TeacherSection> =
    distinctBy {
        "${it.grade.trim().lowercase()}||${it.section.trim().lowercase()}"
    }.sortedWith(
        compareBy<TeacherSection>(
            { gradeSortKey(it.grade) },
            { it.section.lowercase() }
        )
    )

private fun TeacherSection.samePlacement(other: TeacherSection): Boolean =
    grade.equals(other.grade, ignoreCase = true) &&
        section.equals(other.section, ignoreCase = true)

private fun gradeSortKey(grade: String): String {
    val numeric = Regex("\\d+")
        .find(grade)
        ?.value
        ?.toIntOrNull()

    return if (numeric != null) {
        "%05d".format(numeric)
    } else {
        "99999-${grade.lowercase()}"
    }
}
