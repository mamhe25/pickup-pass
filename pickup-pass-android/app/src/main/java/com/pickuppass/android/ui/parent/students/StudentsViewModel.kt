package com.pickuppass.android.ui.parent.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentsUiState(
    val isLoading: Boolean = true,
    val students: List<Student> = emptyList(),
    val error: String? = null,
    val parentDisplayName: String = "",
    val school: SchoolInfo? = null,
    val unreadNotificationCount: Int = 0,
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentsUiState())
    val uiState: StateFlow<StudentsUiState> = _uiState

    private var loadInProgress = false

    init {
        load()
    }

    fun load() {
        if (loadInProgress) return
        loadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val session = authRepository.currentSession()
                if (session == null || session.schoolId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired — please sign in again"
                    )
                    return@launch
                }

                // Independent reads run concurrently so parent home does not
                // wait for branding, notifications and identity sequentially.
                val schoolDeferred = async {
                    studentRepository.getSchool(session.schoolId)
                }
                val unreadDeferred = async {
                    notificationRepository.getUnreadCount(session.uid)
                }
                val profileDeferred = async {
                    studentRepository.getUserProfile(session.uid)
                }
                val studentsDeferred = async {
                    studentRepository.getMyStudents(session.uid, session.schoolId)
                }

                val studentsResult = studentsDeferred.await()
                val school = schoolDeferred.await().getOrNull()
                val unread = unreadDeferred.await().getOrNull() ?: 0
                val profile = profileDeferred.await().getOrNull()

                studentsResult
                    .onSuccess { students ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            students = students.sortedBy { it.fullName.lowercase() },
                            school = school,
                            unreadNotificationCount = unread,
                            parentDisplayName = profile?.displayName.orEmpty(),
                            error = null
                        )
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            school = school,
                            unreadNotificationCount = unread,
                            parentDisplayName = profile?.displayName.orEmpty(),
                            error = "Couldn't load your students"
                        )
                    }
            } finally {
                loadInProgress = false
            }
        }
    }

    fun signOut() = authRepository.signOut()
}
