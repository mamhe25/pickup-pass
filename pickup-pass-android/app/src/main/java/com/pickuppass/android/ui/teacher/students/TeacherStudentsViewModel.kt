package com.pickuppass.android.ui.teacher.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AcademicPlacementOption
import com.pickuppass.android.data.model.AcademicStructureResponse
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.TeacherSection
import com.pickuppass.android.data.model.currentPlacementOptions
import com.pickuppass.android.data.model.currentPlacementOptionsFor
import com.pickuppass.android.data.model.placementKey
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherOperationsRepository
import com.pickuppass.android.data.repository.TeacherRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TeacherStudentsUiState(
    val isLoading: Boolean = true,
    val allStudents: List<Student> = emptyList(),
    val school: SchoolInfo? = null,
    val error: String? = null,
    val placementError: String? = null,
    val role: UserRole = UserRole.Unknown,
    val hasNoAssignedSections: Boolean = false,
    val availablePlacements: List<AcademicPlacementOption> = emptyList(),
    val selectedPlacementFilter: AcademicPlacementOption? = null,
    val searchTerm: String = "",
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    val justCreatedStudentId: String? = null
) {
    val filteredStudents: List<Student>
        get() {
            val placementFiltered = selectedPlacementFilter?.let { selected ->
                allStudents.filter {
                    placementKey(it.grade, it.section) ==
                        placementKey(selected.grade, selected.section)
                }
            } ?: allStudents

            val searched = if (searchTerm.isBlank()) {
                placementFiltered
            } else {
                placementFiltered.filter {
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
                val schoolId = session?.schoolId
                if (session == null || schoolId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired — please sign in again"
                    )
                    return@launch
                }

                val schoolDeferred = async {
                    studentRepository.getSchool(schoolId)
                }
                val structureDeferred = async {
                    teacherOperationsRepository.getAcademicStructure()
                }

                if (session.role == UserRole.Teacher) {
                    loadTeacherRoster(
                        uid = session.uid,
                        schoolId = schoolId,
                        schoolDeferred = schoolDeferred,
                        structureDeferred = structureDeferred
                    )
                    return@launch
                }

                loadSchoolAdminRoster(
                    schoolId = schoolId,
                    schoolDeferred = schoolDeferred,
                    structureDeferred = structureDeferred,
                    role = session.role
                )
            } finally {
                loadInProgress = false
            }
        }
    }

    private suspend fun loadTeacherRoster(
        uid: String,
        schoolId: String,
        schoolDeferred: Deferred<Result<SchoolInfo?>>,
        structureDeferred: Deferred<ApiResult<AcademicStructureResponse>>
    ) {
        val sectionsResult = teacherRepository.getMyAssignedSections(uid)
        val assignedSections = sectionsResult
            .getOrElse {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    role = UserRole.Teacher,
                    school = schoolDeferred.await().getOrNull(),
                    error = "Couldn't load your assigned sections"
                )
                return
            }
            .normalizedSections()

        if (assignedSections.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                role = UserRole.Teacher,
                school = schoolDeferred.await().getOrNull(),
                hasNoAssignedSections = true,
                allStudents = emptyList(),
                availablePlacements = emptyList(),
                selectedPlacementFilter = null
            )
            return
        }

        val studentsDeferred = viewModelScope.async {
            teacherRepository.getStudentsForSections(
                schoolId = schoolId,
                sections = assignedSections
            )
        }

        val structureResult = structureDeferred.await()
        val structure = (structureResult as? ApiResult.Success)?.data
        val availablePlacements = structure
            ?.currentPlacementOptionsFor(assignedSections)
            .orEmpty()
        val currentYear = structure?.currentAcademicYear
        val currentConfiguredCount = structure?.currentPlacementOptions()?.size ?: 0

        val legacyAssignmentCount = assignedSections.count { assigned ->
            availablePlacements.none {
                placementKey(it.grade, it.section) ==
                    placementKey(assigned.grade, assigned.section)
            }
        }

        val placementError = when {
            structureResult is ApiResult.Failure ->
                "School Year & Sections could not be loaded. Existing roster records remain visible, but adding students is disabled."

            currentYear == null ->
                "Your school has no current academic year. Ask the school administrator to set one before adding students."

            currentConfiguredCount == 0 ->
                "No active grade and section is configured for " +
                    currentYear.name.ifBlank { "the current school year" } + "."

            availablePlacements.isEmpty() ->
                "Your assigned sections are not part of the current school-year setup. Ask the school administrator to update your assignments."

            legacyAssignmentCount > 0 ->
                legacyAssignmentCount.toString() + " older assignment" +
                    (if (legacyAssignmentCount == 1) " is" else "s are") +
                    " kept for existing roster access but cannot be used for new students."

            else -> null
        }

        val studentsResult = studentsDeferred.await()
        val school = schoolDeferred.await().getOrNull()

        studentsResult
            .onSuccess { students ->
                val previousFilter = _uiState.value.selectedPlacementFilter
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    role = UserRole.Teacher,
                    school = school,
                    allStudents = students,
                    availablePlacements = availablePlacements,
                    selectedPlacementFilter = previousFilter?.takeIf { selected ->
                        availablePlacements.any {
                            it.gradeSectionId == selected.gradeSectionId
                        }
                    },
                    placementError = placementError,
                    error = null
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    role = UserRole.Teacher,
                    school = school,
                    availablePlacements = availablePlacements,
                    placementError = placementError,
                    error = "Couldn't load students"
                )
            }
    }

    private suspend fun loadSchoolAdminRoster(
        schoolId: String,
        schoolDeferred: Deferred<Result<SchoolInfo?>>,
        structureDeferred: Deferred<ApiResult<AcademicStructureResponse>>,
        role: UserRole
    ) {
        val studentsDeferred = viewModelScope.async {
            teacherRepository.getSchoolStudents(schoolId)
        }

        val structureResult = structureDeferred.await()
        val structure = (structureResult as? ApiResult.Success)?.data
        val availablePlacements = structure?.currentPlacementOptions().orEmpty()

        val placementError = when {
            structureResult is ApiResult.Failure ->
                "School Year & Sections could not be loaded. Existing roster data is available, but adding a student is disabled."

            structure?.currentAcademicYear == null ->
                "Set a current academic year in School Year & Sections before adding students."

            availablePlacements.isEmpty() ->
                "No active grade and section is configured for the current academic year."

            else -> null
        }

        val studentsResult = studentsDeferred.await()
        val school = schoolDeferred.await().getOrNull()

        studentsResult
            .onSuccess { students ->
                val previousFilter = _uiState.value.selectedPlacementFilter
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    role = role,
                    school = school,
                    allStudents = students,
                    availablePlacements = availablePlacements,
                    selectedPlacementFilter = previousFilter?.takeIf { selected ->
                        availablePlacements.any {
                            it.gradeSectionId == selected.gradeSectionId
                        }
                    },
                    placementError = placementError,
                    error = null
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    role = role,
                    school = school,
                    availablePlacements = availablePlacements,
                    placementError = placementError,
                    error = "Couldn't load students"
                )
            }
    }

    fun onSearchChange(term: String) {
        _uiState.value = _uiState.value.copy(
            searchTerm = term.take(80)
        )
    }

    fun onPlacementFilterChange(placement: AcademicPlacementOption?) {
        _uiState.value = _uiState.value.copy(
            selectedPlacementFilter = placement
        )
    }

    fun addStudent(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        placement: AcademicPlacementOption
    ) {
        if (_uiState.value.isSubmitting) return

        if (lastName.isBlank() || firstName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                formError = "Enter the student's last name and first name"
            )
            return
        }

        val allowedPlacement = _uiState.value.availablePlacements
            .firstOrNull { it.gradeSectionId == placement.gradeSectionId }

        if (allowedPlacement == null) {
            _uiState.value = _uiState.value.copy(
                formError = "Choose a current grade and section available to this account"
            )
            return
        }

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
                    grade = allowedPlacement.grade,
                    section = allowedPlacement.section,
                    gradeSectionId = allowedPlacement.gradeSectionId,
                    academicYearId = allowedPlacement.academicYearId
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
        _uiState.value = _uiState.value.copy(formError = null)
    }

    fun consumeJustCreatedStudentId() {
        _uiState.value = _uiState.value.copy(justCreatedStudentId = null)
    }
}

private fun List<TeacherSection>.normalizedSections(): List<TeacherSection> =
    distinctBy { placementKey(it.grade, it.section) }
        .sortedWith(
            compareBy<TeacherSection>(
                { gradeSortKey(it.grade) },
                { it.section.lowercase() }
            )
        )

private fun gradeSortKey(grade: String): String {
    val numeric = Regex("\\d+")
        .find(grade)
        ?.value
        ?.toIntOrNull()

    return if (numeric != null) {
        "%05d".format(numeric)
    } else {
        "99999-" + grade.lowercase()
    }
}
