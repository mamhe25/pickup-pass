package com.pickuppass.android.ui.teacher.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.TeacherSection
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class TeacherHomeSectionSummary(
    val grade: String,
    val section: String,
    val studentCount: Int
) {
    val label: String
        get() = listOf(grade, section)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "Assigned section" }
}

data class TeacherHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val displayName: String = "",
    val school: SchoolInfo? = null,
    val studentCount: Int = 0,
    val sectionSummaries: List<TeacherHomeSectionSummary> = emptyList(),
    val unreadNotifications: Int = 0,
    val hasNoAssignedSections: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TeacherHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherHomeUiState())
    val uiState: StateFlow<TeacherHomeUiState> = _uiState

    private var loadInProgress = false
    private var hasLoaded = false

    init {
        observeUnreadNotifications()
        refresh()
    }

    private fun observeUnreadNotifications() {
        viewModelScope.launch {
            val uid =
                authRepository.currentUid()
                    ?: return@launch

            notificationRepository
                .observeUnreadCount(uid)
                .catch {
                    // Keep the last known count; regular screen refresh remains
                    // a fallback if the live listener is temporarily unavailable.
                }
                .collect { count ->
                    _uiState.value =
                        _uiState.value.copy(
                            unreadNotifications = count
                        )
                }
        }
    }

    fun refresh() {
        if (loadInProgress) return
        loadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = !hasLoaded,
                    isRefreshing = hasLoaded,
                    error = null
                )

                val session = authRepository.currentSession()
                if (session == null || session.role != UserRole.Teacher || session.schoolId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Your teacher session is no longer available. Please sign in again."
                    )
                    return@launch
                }

                val schoolId = requireNotNull(session.schoolId)
                val profileDeferred = async {
                    studentRepository.getUserProfile(session.uid)
                }
                val schoolDeferred = async {
                    studentRepository.getSchool(schoolId)
                }
                val sectionsDeferred = async {
                    teacherRepository.getMyAssignedSections(session.uid)
                }

                val profileResult = profileDeferred.await()
                val schoolResult = schoolDeferred.await()
                val sectionsResult = sectionsDeferred.await()

                val sections = sectionsResult
                    .getOrElse { emptyList() }
                    .normalized()

                val studentsResult = if (sections.isEmpty()) {
                    Result.success(emptyList())
                } else {
                    teacherRepository.getStudentsForSections(
                        schoolId = schoolId,
                        sections = sections
                    )
                }
                val students = studentsResult.getOrElse { emptyList() }

                val summaries = sections.map { assigned ->
                    TeacherHomeSectionSummary(
                        grade = assigned.grade,
                        section = assigned.section,
                        studentCount = students.count { student ->
                            student.grade.equals(assigned.grade, ignoreCase = true) &&
                                student.section.equals(assigned.section, ignoreCase = true)
                        }
                    )
                }

                val partialFailure =
                    profileResult.isFailure ||
                        schoolResult.isFailure ||
                        sectionsResult.isFailure ||
                        studentsResult.isFailure

                _uiState.value = TeacherHomeUiState(
                    isLoading = false,
                    isRefreshing = false,
                    displayName = profileResult.getOrNull()?.displayName.orEmpty(),
                    school = schoolResult.getOrNull(),
                    studentCount = students.size,
                    sectionSummaries = summaries,
                    hasNoAssignedSections = sectionsResult.isSuccess && sections.isEmpty(),
                    error = if (partialFailure) {
                        "Some dashboard information could not be refreshed. Your teacher tools are still available."
                    } else {
                        null
                    }
                )
                hasLoaded = true
            } finally {
                loadInProgress = false
            }
        }
    }
}

private fun List<TeacherSection>.normalized(): List<TeacherSection> =
    distinctBy {
        "${it.grade.trim().lowercase()}||${it.section.trim().lowercase()}"
    }.sortedWith(
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
        "99999-${grade.lowercase()}"
    }
}
