package com.pickuppass.android.ui.parent.pass

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.PickupRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.util.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PickupPassUiState(
    val isLoading: Boolean = true,
    val studentName: String = "",
    val studentGrade: String = "",
    val studentSection: String = "",
    val studentPhotoUrl: String? = null,
    val schoolName: String = "",
    val schoolLogoUrl: String? = null,
    val qrBitmap: Bitmap? = null,
    val expiresAt: Date? = null,
    val secondsRemaining: Long = 0,
    val pickupPolicyText: String = "Any currently valid QR can be presented for pickup.",
    val error: String? = null
)

@HiltViewModel
class PickupPassViewModel @Inject constructor(
    private val pickupRepository: PickupRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickupPassUiState())
    val uiState: StateFlow<PickupPassUiState> = _uiState

    private var countdownJob: Job? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun loadStudentName(studentId: String) {
        viewModelScope.launch {
            studentRepository.getStudent(studentId).onSuccess { student ->
                if (student == null) return@onSuccess

                _uiState.value = _uiState.value.copy(
                    studentName = student.fullName,
                    studentGrade = student.grade,
                    studentSection = student.section,
                    studentPhotoUrl = student.photoUrl
                )

                if (student.schoolId.isBlank()) return@onSuccess

                studentRepository.getSchool(student.schoolId).onSuccess { school ->
                    val policy = school?.pickupPolicy
                    val policyText =
                        if (
                            policy?.mode == "time_window" &&
                            policy.earliestPickupTime.isNotBlank() &&
                            policy.latestPickupTime.isNotBlank()
                        ) {
                            "School pickup window: ${policy.earliestPickupTime}–${policy.latestPickupTime}."
                        } else {
                            "Any currently valid QR can be presented for pickup."
                        }

                    _uiState.value = _uiState.value.copy(
                        schoolName = school?.schoolName.orEmpty(),
                        schoolLogoUrl = school?.logoUrl,
                        pickupPolicyText = policyText
                    )
                }
            }
        }
    }

    fun generatePass(studentId: String) {
        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                qrBitmap = null,
                expiresAt = null,
                secondsRemaining = 0
            )

            when (val result = pickupRepository.generateToken(studentId)) {
                is ApiResult.Success -> {
                    val expiresAt = parseIso(result.data.expiresAt)
                    if (expiresAt == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "The pass expiry could not be verified. Please generate a new pass."
                        )
                        return@launch
                    }

                    val bitmap = runCatching {
                        QrCodeGenerator.generate(result.data.qrToken)
                    }.getOrElse {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "The secure QR could not be created. Please try again."
                        )
                        return@launch
                    }

                    val initialRemaining =
                        ((expiresAt.time - System.currentTimeMillis() + 999L) / 1000L)
                            .coerceAtLeast(0L)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        qrBitmap = bitmap,
                        expiresAt = expiresAt,
                        secondsRemaining = initialRemaining
                    )
                    startCountdown(expiresAt)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    private fun startCountdown(expiresAt: Date) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val remaining =
                    ((expiresAt.time - System.currentTimeMillis() + 999L) / 1000L)
                        .coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(secondsRemaining = remaining)
                if (remaining <= 0L) break
                delay(1000L)
            }
        }
    }

    private fun parseIso(value: String): Date? = try {
        isoFormat.parse(value)
    } catch (_: Exception) {
        null
    }
}
