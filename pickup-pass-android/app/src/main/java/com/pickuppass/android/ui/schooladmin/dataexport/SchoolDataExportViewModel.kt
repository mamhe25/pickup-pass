package com.pickuppass.android.ui.schooladmin.dataexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SchoolDataExportUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val message: String = "",
    val exporting: Boolean = false,
    val exportFileName: String? = null,
    val exportBytes: ByteArray? = null,
    val error: String? = null
)

@HiltViewModel
class SchoolDataExportViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SchoolDataExportUiState())
    val uiState: StateFlow<SchoolDataExportUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        when (val result = repository.getSchoolDataExportStatus()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                loading = false,
                enabled = result.data.enabled,
                message = result.data.message
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                loading = false,
                error = result.message
            )
        }
    }

    fun createExport() {
        if (_uiState.value.exporting || !_uiState.value.enabled) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exporting = true, error = null)
            when (val result = repository.downloadSchoolDataExport()) {
                is ApiResult.Success -> {
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .withZone(ZoneOffset.UTC).format(Instant.now())
                    _uiState.value = _uiState.value.copy(
                        exporting = false,
                        exportFileName = "PickupPass_School_Data_Export_$stamp.zip",
                        exportBytes = result.data
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    exporting = false,
                    error = result.message
                )
            }
        }
    }

    fun clearExport() {
        _uiState.value = _uiState.value.copy(exportFileName = null, exportBytes = null)
    }
}
