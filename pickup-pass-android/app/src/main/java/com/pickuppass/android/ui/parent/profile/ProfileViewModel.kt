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
    val error: String? = null,
    val uploadSuccessMessage: String? = null
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

    private fun load() {
        viewModelScope.launch {
            val uid = authRepository.currentUid() ?: return@launch
            studentRepository.getUserProfile(uid).onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    displayName = profile?.displayName.orEmpty(),
                    email = profile?.email.orEmpty(),
                    photoUrl = profile?.photoUrl
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load your profile")
            }
        }
    }

    fun onImagePicked(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null, uploadSuccessMessage = null)

            val session = authRepository.currentSession()
            if (session == null || session.schoolId == null) {
                _uiState.value = _uiState.value.copy(isUploading = false, error = "Session expired")
                return@launch
            }

            profileRepository.uploadAvatar(context, session.uid, session.schoolId, uri)
                .onSuccess { url ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        photoUrl = url,
                        uploadSuccessMessage = "Photo updated"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "Upload failed: ${it.message ?: "unknown error"}"
                    )
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            notificationRepository.unregisterCurrentDeviceToken()
            authRepository.signOut()
            _signedOut.value = true
        }
    }
}
