package com.pickuppass.android.ui.schooladmin.bulkimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.BulkStudentImportResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BulkStudentImportUiState(
    val filename: String = "",
    val isWorking: Boolean = false,
    val preview: BulkStudentImportResponse? = null,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class BulkStudentImportViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkStudentImportUiState())
    val uiState: StateFlow<BulkStudentImportUiState> = _uiState

    private var selectedBytes: ByteArray? = null
    private var selectedFilename: String = ""

    fun selectFile(context: Context, uri: Uri) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.value = BulkStudentImportUiState(isWorking = true)
            try {
                val pair = withContext(Dispatchers.IO) {
                    val name = resolveFileName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes()
                    } ?: error("Could not read the selected file")
                    name to bytes
                }
                val (name, bytes) = pair
                if (bytes.size > 10 * 1024 * 1024) {
                    _uiState.value = BulkStudentImportUiState(error = "File must be 10 MB or smaller")
                    return@launch
                }
                if (!(name.endsWith(".csv", true) || name.endsWith(".xlsx", true) || name.endsWith(".xls", true))) {
                    _uiState.value = BulkStudentImportUiState(error = "Choose a CSV or Excel (.xlsx/.xls) file")
                    return@launch
                }
                selectedBytes = bytes
                selectedFilename = name
                previewSelectedFile()
            } catch (e: Exception) {
                _uiState.value = BulkStudentImportUiState(error = e.message ?: "Could not read file")
            }
        }
    }

    private suspend fun previewSelectedFile() {
        val bytes = selectedBytes ?: return
        _uiState.value = _uiState.value.copy(filename = selectedFilename, isWorking = true, error = null, success = null)
        when (val result = repository.importStudents(bytes, selectedFilename, dryRun = true)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(isWorking = false, preview = result.data)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isWorking = false, error = result.message)
        }
    }

    fun importConfirmed() {
        val bytes = selectedBytes ?: return
        val preview = _uiState.value.preview ?: return
        if (!preview.readyToImport || preview.invalidRows > 0 || _uiState.value.isWorking) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, error = null, success = null)
            when (val result = repository.importStudents(bytes, selectedFilename, dryRun = false)) {
                is ApiResult.Success -> {
                    val body = result.data
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        preview = body,
                        success = "Imported ${body.importedRows} student${if (body.importedRows == 1) "" else "s"}. " +
                                if (body.duplicateRows > 0) "Skipped ${body.duplicateRows} duplicate row(s)." else ""
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isWorking = false, error = result.message)
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: "students.csv"
    }
}
