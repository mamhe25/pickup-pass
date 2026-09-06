package com.pickuppass.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val roleLabel: String = "",
    val roleDescription: String = "",
    val schoolName: String = "",
    val error: String? = null
)

@HiltViewModel
class AccountProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountProfileUiState())
    val uiState: StateFlow<AccountProfileUiState> = _uiState

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val session = authRepository.currentSession()
            if (session == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Session expired — please sign in again."
                )
                return@launch
            }

            val profile = studentRepository
                .getUserProfile(session.uid)
                .getOrNull()

            val schoolName = session.schoolId
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    studentRepository
                        .getSchool(it)
                        .getOrNull()
                        ?.schoolName
                }
                .orEmpty()

            val role = session.role.toRolePresentation()

            _uiState.value = AccountProfileUiState(
                isLoading = false,
                displayName = profile?.displayName.orEmpty(),
                email = authRepository.currentEmail().ifBlank {
                    profile?.email.orEmpty()
                },
                photoUrl = profile?.photoUrl,
                roleLabel = role.first,
                roleDescription = role.second,
                schoolName = schoolName,
                error = if (profile == null) {
                    "Some profile details are unavailable right now."
                } else {
                    null
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            notificationRepository.unregisterCurrentDeviceToken()
            authRepository.signOut()
            _signedOut.value = true
        }
    }

    private fun UserRole.toRolePresentation(): Pair<String, String> =
        when (this) {
            UserRole.Teacher ->
                "Teacher" to "School staff account for student dismissal and guardian verification."
            UserRole.SchoolAdmin ->
                "School administrator" to "School operations account with administrative access."
            UserRole.MasterAdmin ->
                "Platform owner" to "PickupPass platform administration account."
            UserRole.Parent ->
                "Parent / guardian" to "Family pickup account."
            UserRole.Unknown ->
                "PickupPass user" to "Signed-in PickupPass account."
        }
}
