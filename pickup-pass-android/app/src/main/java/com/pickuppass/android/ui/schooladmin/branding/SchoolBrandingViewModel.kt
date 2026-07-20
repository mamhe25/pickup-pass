package com.pickuppass.android.ui.schooladmin.branding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.SchoolRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchoolBrandingUiState(
    val isLoading: Boolean = true,
    val schoolName: String = "",
    val logoUrl: String? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SchoolBrandingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val schoolRepository: SchoolRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchoolBrandingUiState())
    val uiState: StateFlow<SchoolBrandingUiState> = _uiState

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val session = authRepository.currentSession()
            if (session?.schoolId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }

            studentRepository.getSchool(session.schoolId)
                .onSuccess { school ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        schoolName = school?.schoolName.orEmpty(),
                        logoUrl = school?.logoUrl
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load school info")
                }
        }
    }

    fun onImagePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null, successMessage = null)

            when (val result = schoolRepository.uploadLogo(context, uri)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        logoUrl = result.data,
                        successMessage = "Logo updated!"
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isUploading = false, error = result.message)
                }
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
