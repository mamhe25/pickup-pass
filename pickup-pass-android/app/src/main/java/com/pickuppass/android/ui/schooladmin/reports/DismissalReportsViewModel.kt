package com.pickuppass.android.ui.schooladmin.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.DismissalReportSummary
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportExportPayload(val fileName: String, val bytes: ByteArray)

data class DismissalReportsUiState(
    val from: String = LocalDate.now().minusDays(6).toString(),
    val to: String = LocalDate.now().toString(),
    val grade: String = "",
    val section: String = "",
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null,
    val summary: DismissalReportSummary? = null,
    val exportPayload: ReportExportPayload? = null
)

@HiltViewModel
class DismissalReportsViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DismissalReportsUiState())
    val uiState: StateFlow<DismissalReportsUiState> = _uiState

    init { load() }

    fun setFrom(value: String) { _uiState.value = _uiState.value.copy(from = value) }
    fun setTo(value: String) { _uiState.value = _uiState.value.copy(to = value) }
    fun setGrade(value: String) { _uiState.value = _uiState.value.copy(grade = value) }
    fun setSection(value: String) { _uiState.value = _uiState.value.copy(section = value) }

    fun load() {
        val s = _uiState.value
        if (!validRange(s.from, s.to)) {
            _uiState.value = s.copy(error = "Use YYYY-MM-DD and make sure To is not before From.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getDismissalReportSummary(
                s.from, s.to, s.grade.trim().ifBlank { null }, s.section.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false, summary = result.data, error = null
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
            }
        }
    }

    fun exportCsv() {
        val s = _uiState.value
        if (!validRange(s.from, s.to)) {
            _uiState.value = s.copy(error = "Use YYYY-MM-DD and make sure To is not before From.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            when (val result = repository.exportDismissalReportCsv(
                s.from, s.to, s.grade.trim().ifBlank { null }, s.section.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportPayload = ReportExportPayload(
                        fileName = "pickuppass-dismissals-${s.from}-to-${s.to}.csv",
                        bytes = result.data
                    )
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isExporting = false, error = result.message
                )
            }
        }
    }

    fun consumeExport() { _uiState.value = _uiState.value.copy(exportPayload = null) }
    fun setError(message: String) { _uiState.value = _uiState.value.copy(error = message) }

    private fun validRange(from: String, to: String): Boolean = try {
        val f = LocalDate.parse(from)
        val t = LocalDate.parse(to)
        !t.isBefore(f)
    } catch (_: Exception) { false }
}
