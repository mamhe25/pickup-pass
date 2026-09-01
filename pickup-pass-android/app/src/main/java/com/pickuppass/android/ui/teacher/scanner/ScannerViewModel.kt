package com.pickuppass.android.ui.teacher.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.PickupRepository
import com.pickuppass.android.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScannerUiState {
    data object Scanning : ScannerUiState()
    data object Verifying : ScannerUiState()

    data class Verified(
        val student: Student,
        val guardian: UserProfile,
        val qrToken: String,
        val isApproving: Boolean = false
    ) : ScannerUiState()

    data class Error(val message: String) : ScannerUiState()
    data class Approved(val gateLabel: String = "") : ScannerUiState()
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

    private val _pickupGates = MutableStateFlow<List<PickupGateItem>>(emptyList())
    val pickupGates: StateFlow<List<PickupGateItem>> = _pickupGates

    private val _selectedPickupGate = MutableStateFlow<PickupGateItem?>(null)
    val selectedPickupGate: StateFlow<PickupGateItem?> = _selectedPickupGate

    private val _gateLoading = MutableStateFlow(true)
    val gateLoading: StateFlow<Boolean> = _gateLoading

    private val _gateError = MutableStateFlow<String?>(null)
    val gateError: StateFlow<String?> = _gateError

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut

    private var isProcessing = false

    init {
        viewModelScope.launch {
            val session = authRepository.currentSession()
            session?.schoolId?.let { schoolId ->
                studentRepository.getSchool(schoolId).onSuccess { _school.value = it }
            }
        }
        loadPickupGates()
    }

    fun loadPickupGates() {
        viewModelScope.launch {
            _gateLoading.value = true
            _gateError.value = null

            when (val result = pickupRepository.getActivePickupGates()) {
                is ApiResult.Success -> {
                    val gates = result.data
                    _pickupGates.value = gates

                    val current = _selectedPickupGate.value
                    _selectedPickupGate.value = when {
                        current != null && gates.any { it.id == current.id } ->
                            gates.first { it.id == current.id }
                        gates.size == 1 -> gates.first()
                        else -> null
                    }
                }

                is ApiResult.Failure -> {
                    _pickupGates.value = emptyList()
                    _selectedPickupGate.value = null
                    _gateError.value =
                        scanFailureMessage(result.message, "Pickup gate loading")
                }
            }

            _gateLoading.value = false
        }
    }

    fun selectPickupGate(gate: PickupGateItem) {
        if (_pickupGates.value.any { it.id == gate.id }) {
            _selectedPickupGate.value = gate
        }
    }

    fun onQrCodeScanned(qrToken: String) {
        if (isProcessing) return
        if (_gateLoading.value || _gateError.value != null) return

        if (_pickupGates.value.isNotEmpty() && _selectedPickupGate.value == null) {
            _uiState.value =
                ScannerUiState.Error("Select the pickup gate being used before scanning a pass")
            return
        }

        isProcessing = true

        viewModelScope.launch {
            _uiState.value = ScannerUiState.Verifying

            when (val result = pickupRepository.verify(qrToken)) {
                is ApiResult.Success -> {
                    val studentId = result.data.studentId
                    val parentUid = result.data.parentUid

                    if (studentId == null || parentUid == null) {
                        _uiState.value =
                            ScannerUiState.Error("Malformed verification response from server")
                        return@launch
                    }

                    val student = studentRepository.getStudent(studentId).getOrNull()
                    val guardian = studentRepository.getUserProfile(parentUid).getOrNull()

                    when {
                        student == null -> {
                            _uiState.value =
                                ScannerUiState.Error("Student record could not be loaded. Do not release the student.")
                        }

                        guardian == null -> {
                            _uiState.value =
                                ScannerUiState.Error(
                                    "Authorized guardian profile could not be loaded. " +
                                        "Do not release the student."
                                )
                        }

                        else -> {
                            _uiState.value = ScannerUiState.Verified(
                                student = student,
                                guardian = guardian,
                                qrToken = qrToken
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        ScannerUiState.Error(scanFailureMessage(result.message, "Verification"))
                }
            }
        }
    }

    fun approveRelease() {
        val current = _uiState.value
        if (current !is ScannerUiState.Verified || current.isApproving) return

        if (current.guardian.photoUrl.isNullOrBlank()) {
            _uiState.value = ScannerUiState.Error(
                "Guardian identity photo is unavailable. Do not approve a QR release. " +
                    "Use the school's manual identity-verification process instead."
            )
            return
        }

        val gate = _selectedPickupGate.value
        if (_pickupGates.value.isNotEmpty() && gate == null) {
            _uiState.value =
                ScannerUiState.Error("Select the pickup gate before approving release")
            return
        }

        _uiState.value = current.copy(isApproving = true)

        viewModelScope.launch {
            when (val result = pickupRepository.approve(current.qrToken, gate?.id)) {
                is ApiResult.Success -> {
                    _uiState.value =
                        ScannerUiState.Approved(gate?.displayName.orEmpty())
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        ScannerUiState.Error(scanFailureMessage(result.message, "Approval"))
                }
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
            "$operation timed out. Check that the backend is reachable, then try again."
        } else {
            message
        }
}