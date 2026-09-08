package com.pickuppass.android.ui.schooladmin.dataexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SchoolDataExportUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val enabled: Boolean = false,
    val storageMode: String = "direct_download",
    val cloudCopyCreated: Boolean = false,
    val platformBackupControlledBy: String = "master_admin",
    val statusMessage: String = "",
    val exporting: Boolean = false,
    val exportFileName: String? = null,
    val error: String? = null,
    val errorTitle: String? = null,
    val success: String? = null,
    val successTitle: String? = null
)

@HiltViewModel
class SchoolDataExportViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(SchoolDataExportUiState())
    val uiState: StateFlow<SchoolDataExportUiState> =
        _uiState

    private var loadInProgress = false

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        if (loadInProgress || _uiState.value.exporting) return

        loadInProgress = true

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    loading = initial,
                    refreshing = !initial,
                    error = null,
                    errorTitle = null
                )

            try {
                when (
                    val result =
                        repository
                            .getSchoolDataExportStatus()
                ) {
                    is ApiResult.Success -> {
                        val status = result.data

                        _uiState.value =
                            _uiState.value.copy(
                                loading = false,
                                refreshing = false,
                                enabled = status.enabled,
                                storageMode =
                                    status.storageMode,
                                cloudCopyCreated =
                                    status.cloudCopyCreated,
                                platformBackupControlledBy =
                                    status
                                        .platformBackupControlledBy,
                                statusMessage =
                                    status.message,
                                error = null,
                                errorTitle = null
                            )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value =
                            _uiState.value.copy(
                                loading = false,
                                refreshing = false,
                                errorTitle =
                                    if (initial) {
                                        "Export status unavailable"
                                    } else {
                                        "Export status not refreshed"
                                    },
                                error = result.message
                            )
                    }
                }
            } finally {
                loadInProgress = false
            }
        }
    }

    fun requestExportSave() {
        if (
            _uiState.value.exporting ||
            !_uiState.value.enabled
        ) {
            return
        }

        val stamp =
            DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())

        _uiState.value =
            _uiState.value.copy(
                exportFileName =
                    "PickupPass_School_Data_Export_" +
                        stamp +
                        ".zip",
                error = null,
                errorTitle = null,
                success = null,
                successTitle = null
            )
    }

    fun createExport(
        context: Context,
        uri: Uri
    ) {
        val fileName =
            _uiState.value.exportFileName
                ?: return

        if (
            _uiState.value.exporting ||
            !_uiState.value.enabled
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    exporting = true,
                    error = null,
                    errorTitle = null,
                    success = null,
                    successTitle = null
                )

            val result =
                withContext(Dispatchers.IO) {
                    val output =
                        context.contentResolver
                            .openOutputStream(uri)
                            ?: return@withContext ApiResult.Failure(
                                "Could not open the selected save location"
                            )

                    output.use {
                        repository
                            .downloadSchoolDataExport(
                                it
                            )
                    }
                }

            when (result) {
                is ApiResult.Success -> {
                    val payload = result.data

                    _uiState.value =
                        _uiState.value.copy(
                            exporting = false,
                            exportFileName = null,
                            successTitle =
                                "School export saved",
                            success =
                                fileName +
                                    " was saved successfully. " +
                                    formatBytes(
                                        payload.bytesWritten
                                    ) +
                                    " written. SHA-256: " +
                                    payload.sha256 +
                                    ". Store this archive as confidential school data."
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            exporting = false,
                            exportFileName = null,
                            errorTitle =
                                "School export not saved",
                            error =
                                "The export could not be completed at the selected destination. " +
                                    result.message
                        )
                }
            }
        }
    }

    fun cancelExportSave() {
        if (_uiState.value.exporting) return

        _uiState.value =
            _uiState.value.copy(
                exportFileName = null
            )
    }

    fun clearFeedback() {
        _uiState.value =
            _uiState.value.copy(
                error = null,
                errorTitle = null,
                success = null,
                successTitle = null
            )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) {
            return bytes.toString() + " B"
        }

        val kilobytes = bytes / 1024.0

        if (kilobytes < 1024.0) {
            return "%.1f KB".format(kilobytes)
        }

        return "%.1f MB".format(
            kilobytes / 1024.0
        )
    }
}
