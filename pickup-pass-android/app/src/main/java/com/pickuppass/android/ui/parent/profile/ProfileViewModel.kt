package com.pickuppass.android.ui.parent.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.ProfileRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val photoValidationStatus: String = "missing",
    val error: String? = null,
    val errorTitle: String? = null,
    val uploadSuccessMessage: String? = null,
    val uploadSuccessTitle: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val profileRepository: ProfileRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val uid = authRepository.currentUid()
            if (uid == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Session expired — please sign in again"
                )
                return@launch
            }

            studentRepository.getUserProfile(uid)
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        displayName = profile?.displayName.orEmpty(),
                        email = profile?.email.orEmpty(),
                        photoUrl = profile?.photoUrl,
                        photoValidationStatus =
                            profile?.photoValidationStatus
                                .orEmpty()
                                .ifBlank { "missing" },
                        error = null,
                        errorTitle = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Couldn't load your profile",
                        errorTitle = "Profile unavailable"
                    )
                }
        }
    }

    fun onImagePicked(
        context: android.content.Context,
        uri: Uri
    ) {
        if (_uiState.value.isUploading) return

        _uiState.value = _uiState.value.copy(
            isUploading = true,
            error = null,
            errorTitle = null,
            uploadSuccessMessage = null,
            uploadSuccessTitle = null
        )

        viewModelScope.launch {
            val session = authRepository.currentSession()
            if (session == null || session.schoolId == null) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Session expired — please sign in again",
                    errorTitle = "Session expired"
                )
                return@launch
            }

            profileRepository.uploadAvatar(
                context,
                session.uid,
                session.schoolId,
                uri
            )
                .let { result ->
                    when (result) {
                        is com.pickuppass.android.data.repository.ApiResult.Success -> {
                            val response = result.data
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                photoUrl = response.photoUrl,
                                photoValidationStatus = "verified",
                                uploadSuccessTitle = "Photo verified",
                                uploadSuccessMessage =
                                    response.message
                                        ?: "Your verification photo is ready for school pickup checks."
                            )
                        }

                        is com.pickuppass.android.data.repository.ApiResult.Failure -> {
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                errorTitle = "Photo not accepted",
                                error = result.message
                            )
                        }
                    }
                }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            errorTitle = null,
            uploadSuccessMessage = null,
            uploadSuccessTitle = null
        )
    }

    fun signOut() {
        viewModelScope.launch {
            notificationRepository.unregisterCurrentDeviceToken()
            authRepository.signOut()
            _signedOut.value = true
        }
    }
}
