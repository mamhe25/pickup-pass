package com.pickuppass.android.ui.teacher.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.PickupRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScannerUiState {
    data object Scanning : ScannerUiState()
    data object Verifying : ScannerUiState()
    data class Verified(
        val student: Student,
        val guardian: UserProfile?,
        val qrToken: String,
        val isApproving: Boolean = false
    ) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
    data object Approved : ScannerUiState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val pickupRepository: PickupRepository,
    private val studentRepository: StudentRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
    val uiState: StateFlow<ScannerUiState> = _uiState

    private val _school = MutableStateFlow<SchoolInfo?>(null)
    val school: StateFlow<SchoolInfo?> = _school

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut

    // Guards against ML Kit firing the analyzer multiple times for the same
    // frame burst while a verify call is already in flight.
    private var isProcessing = false

    init {
        viewModelScope.launch {
            val session = authRepository.currentSession()
            session?.schoolId?.let { schoolId ->
                studentRepository.getSchool(schoolId).onSuccess { _school.value = it }
            }
        }
    }

    fun onQrCodeScanned(qrToken: String) {
        if (isProcessing) return
        isProcessing = true

        viewModelScope.launch {
            _uiState.value = ScannerUiState.Verifying

            when (val result = pickupRepository.verify(qrToken)) {
                is ApiResult.Success -> {
                    val studentId = result.data.studentId
                    val parentUid = result.data.parentUid
                    if (studentId == null || parentUid == null) {
                        _uiState.value = ScannerUiState.Error("Malformed response from server")
                        isProcessing = false
                        return@launch
                    }

                    val student = studentRepository.getStudent(studentId).getOrNull()
                    val guardian = studentRepository.getUserProfile(parentUid).getOrNull()

                    if (student == null) {
                        _uiState.value = ScannerUiState.Error("Student record not found")
                    } else {
                        _uiState.value = ScannerUiState.Verified(student, guardian, qrToken)
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.value = ScannerUiState.Error(scanFailureMessage(result.message, "Verification"))
                }
            }
        }
    }

    fun approveRelease() {
        val current = _uiState.value
        if (current !is ScannerUiState.Verified || current.isApproving) return

        // Update before launching so two taps in the same UI frame cannot
        // enqueue duplicate approval requests.
        _uiState.value = current.copy(isApproving = true)
        viewModelScope.launch {
            when (val result = pickupRepository.approve(current.qrToken)) {
                is ApiResult.Success -> _uiState.value = ScannerUiState.Approved
                is ApiResult.Failure -> _uiState.value = ScannerUiState.Error(scanFailureMessage(result.message, "Approval"))
            }
        }
    }

    fun resetToScanning() {
        isProcessing = false
        _uiState.value = ScannerUiState.Scanning
    }

    fun signOut() {
        viewModelScope.launch {
            notificationRepository.unregisterCurrentDeviceToken()
            authRepository.signOut()
            _signedOut.value = true
        }
    }

    private fun scanFailureMessage(message: String, operation: String): String =
        if (message.contains("timeout", ignoreCase = true)) {
            "$operation timed out. Check that the deployed backend is reachable."
        } else {
            message
        }
}
