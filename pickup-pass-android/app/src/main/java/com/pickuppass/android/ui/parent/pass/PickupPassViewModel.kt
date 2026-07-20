package com.pickuppass.android.ui.parent.pass

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.PickupRepository
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.util.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PickupPassUiState(
    val isLoading: Boolean = true,
    val studentName: String = "",
    val qrBitmap: Bitmap? = null,
    val expiresAt: Date? = null,
    val secondsRemaining: Long = 0,
    val error: String? = null
)

@HiltViewModel
class PickupPassViewModel @Inject constructor(
    private val pickupRepository: PickupRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickupPassUiState())
    val uiState: StateFlow<PickupPassUiState> = _uiState

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun loadStudentName(studentId: String) {
        viewModelScope.launch {
            studentRepository.getStudent(studentId).onSuccess { student ->
                _uiState.value = _uiState.value.copy(studentName = student?.fullName.orEmpty())
            }
        }
    }

    fun generatePass(studentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, qrBitmap = null)

            when (val result = pickupRepository.generateToken(studentId)) {
                is ApiResult.Success -> {
                    val bitmap = QrCodeGenerator.generate(result.data.qrToken)
                    val expiresAt = parseIso(result.data.expiresAt)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        qrBitmap = bitmap,
                        expiresAt = expiresAt
                    )
                    startCountdown(expiresAt)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    private fun startCountdown(expiresAt: Date?) {
        if (expiresAt == null) return
        viewModelScope.launch {
            while (isActive) {
                val remaining = (expiresAt.time - System.currentTimeMillis()) / 1000
                _uiState.value = _uiState.value.copy(secondsRemaining = remaining.coerceAtLeast(0))
                if (remaining <= 0) break
                delay(1000)
            }
        }
    }

    private fun parseIso(value: String): Date? = try {
        isoFormat.parse(value)
    } catch (e: Exception) {
        null
    }
}
