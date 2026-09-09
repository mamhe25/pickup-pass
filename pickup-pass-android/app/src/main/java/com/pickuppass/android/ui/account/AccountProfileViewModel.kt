package com.pickuppass.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.UpdateAccountNameRequest
import com.pickuppass.android.data.remote.PickupPassApi
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
    val canEditName: Boolean = false,
    val nameBusy: Boolean = false,
    val nameError: String? = null,
    val nameSuccess: String? = null,
    val error: String? = null
)

@HiltViewModel
class AccountProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: PickupPassApi,
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
                canEditName =
                    session.role is UserRole.MasterAdmin,
                nameBusy = false,
                nameError = null,
                nameSuccess = null,
                error = if (profile == null) {
                    "Some profile details are unavailable right now."
                } else {
                    null
                }
            )
        }
    }

    fun updateDisplayName(
        newName: String
    ) {
        val state = _uiState.value

        if (!state.canEditName || state.nameBusy) {
            return
        }

        val normalized =
            newName.trim()
                .replace(Regex("\\s+"), " ")

        val validationError =
            when {
                normalized.length < 2 ->
                    "Enter at least 2 characters."

                normalized.length > 80 ->
                    "Name must be 80 characters or fewer."

                normalized == state.displayName.trim() ->
                    "Enter a different name."

                else ->
                    null
            }

        if (validationError != null) {
            _uiState.value = state.copy(
                nameError = validationError,
                nameSuccess = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                nameBusy = true,
                nameError = null,
                nameSuccess = null
            )

            runCatching {
                api.updateMasterProfileName(
                    UpdateAccountNameRequest(
                        displayName = normalized
                    )
                )
            }.onSuccess { response ->
                val updated =
                    response.body()?.displayName
                        ?.takeIf { it.isNotBlank() }

                if (response.isSuccessful && updated != null) {
                    _uiState.value = _uiState.value.copy(
                        nameBusy = false,
                        displayName = updated,
                        nameSuccess = "Name updated.",
                        nameError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        nameBusy = false,
                        nameError =
                            "Couldn't update your name. Please try again."
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    nameBusy = false,
                    nameError =
                        "Couldn't update your name. Check your connection and try again."
                )
            }
        }
    }

    fun clearNameFeedback() {
        _uiState.value = _uiState.value.copy(
            nameError = null,
            nameSuccess = null
        )
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
