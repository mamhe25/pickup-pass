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
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BulkStudentImportUiState(
    val filename: String = "",
    val isWorking: Boolean = false,
    val workingMessage: String = "",
    val preview: BulkStudentImportResponse? = null,
    val error: String? = null,
    val errorTitle: String? = null,
    val success: String? = null,
    val successTitle: String? = null
)

@HiltViewModel
class BulkStudentImportViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(BulkStudentImportUiState())
    val uiState: StateFlow<BulkStudentImportUiState> =
        _uiState

    private var selectedBytes: ByteArray? = null
    private var selectedFilename: String = ""

    fun selectFile(
        context: Context,
        uri: Uri
    ) {
        if (_uiState.value.isWorking) return

        viewModelScope.launch {
            _uiState.value =
                BulkStudentImportUiState(
                    isWorking = true,
                    workingMessage = "Reading roster…"
                )

            try {
                val selected = withContext(Dispatchers.IO) {
                    val meta =
                        resolveFileMeta(context, uri)

                    validateFilename(meta.name)

                    if (
                        meta.size != null &&
                        meta.size > MAX_FILE_BYTES
                    ) {
                        error(
                            "File must be 10 MB or smaller"
                        )
                    }

                    val bytes =
                        readBytesLimited(
                            context,
                            uri,
                            MAX_FILE_BYTES
                        )

                    SelectedFile(
                        name = meta.name,
                        bytes = bytes
                    )
                }

                selectedBytes = selected.bytes
                .also { selectedBytes = null }
                .copyOf()
                .also {
                    selectedBytes = it
                }
                .let { selectedBytes }

                selectedFilename = selected.name

                previewSelectedFile()
            } catch (e: Exception) {
                selectedBytes = null
                selectedFilename = ""

                _uiState.value =
                    BulkStudentImportUiState(
                        errorTitle =
                            "Roster could not be opened",
                        error =
                            e.message
                                ?: "Could not read the selected file"
                    )
            }
        }
    }

    fun retryValidation() {
        if (
            _uiState.value.isWorking ||
            selectedBytes == null
        ) {
            return
        }

        viewModelScope.launch {
            previewSelectedFile()
        }
    }

    fun importConfirmed() {
        val bytes = selectedBytes ?: return
        val preview = _uiState.value.preview ?: return

        if (
            !preview.readyToImport ||
            preview.invalidRows > 0 ||
            _uiState.value.isWorking
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isWorking = true,
                    workingMessage =
                        "Importing validated students…",
                    error = null,
                    errorTitle = null,
                    success = null,
                    successTitle = null
                )

            when (
                val result =
                    repository.importStudents(
                        bytes,
                        selectedFilename,
                        dryRun = false
                    )
            ) {
                is ApiResult.Success -> {
                    val body = result.data

                    selectedBytes = null
                    selectedFilename = ""

                    _uiState.value =
                        _uiState.value.copy(
                            isWorking = false,
                            workingMessage = "",
                            preview = body,
                            successTitle =
                                "Student import completed",
                            success =
                                buildImportSuccessMessage(body)
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isWorking = false,
                            workingMessage = "",
                            errorTitle =
                                "Student import not completed",
                            error = result.message
                        )
                }
            }
        }
    }

    fun resetImport() {
        if (_uiState.value.isWorking) return

        selectedBytes = null
        selectedFilename = ""
        _uiState.value = BulkStudentImportUiState()
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

    private suspend fun previewSelectedFile() {
        val bytes = selectedBytes ?: return

        _uiState.value =
            _uiState.value.copy(
                filename = selectedFilename,
                isWorking = true,
                workingMessage =
                    "Validating roster without writing data…",
                preview = null,
                error = null,
                errorTitle = null,
                success = null,
                successTitle = null
            )

        when (
            val result =
                repository.importStudents(
                    bytes,
                    selectedFilename,
                    dryRun = true
                )
        ) {
            is ApiResult.Success -> {
                _uiState.value =
                    _uiState.value.copy(
                        isWorking = false,
                        workingMessage = "",
                        preview = result.data
                    )
            }

            is ApiResult.Failure -> {
                _uiState.value =
                    _uiState.value.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorTitle =
                            "Roster validation failed",
                        error = result.message
                    )
            }
        }
    }

    private fun buildImportSuccessMessage(
        body: BulkStudentImportResponse
    ): String {
        val imported =
            body.importedRows.toString() +
                " student" +
                if (body.importedRows == 1) {
                    ""
                } else {
                    "s"
                } +
                " imported."

        return if (body.duplicateRows > 0) {
            imported +
                " " +
                body.duplicateRows +
                " duplicate row" +
                if (body.duplicateRows == 1) {
                    " was skipped."
                } else {
                    "s were skipped."
                }
        } else {
            imported
        }
    }

    private fun validateFilename(name: String) {
        val supported =
            name.endsWith(".csv", true) ||
                name.endsWith(".xlsx", true) ||
                name.endsWith(".xls", true)

        if (!supported) {
            error(
                "Choose a CSV or Excel (.xlsx/.xls) file"
            )
        }
    }

    private fun resolveFileMeta(
        context: Context,
        uri: Uri
    ): FileMeta {
        var name: String? = null
        var size: Long? = null

        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }

                val sizeIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (
                    sizeIndex >= 0 &&
                    !cursor.isNull(sizeIndex)
                ) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return FileMeta(
            name =
                name
                    ?.takeIf { it.isNotBlank() }
                    ?: "students.csv",
            size = size
        )
    }

    private fun readBytesLimited(
        context: Context,
        uri: Uri,
        maxBytes: Long
    ): ByteArray {
        val input =
            context.contentResolver
                .openInputStream(uri)
                ?: error(
                    "Could not read the selected file"
                )

        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L

            while (true) {
                val read = stream.read(buffer)

                if (read < 0) break

                total += read

                if (total > maxBytes) {
                    error(
                        "File must be 10 MB or smaller"
                    )
                }

                output.write(buffer, 0, read)
            }

            return output.toByteArray()
        }
    }

    private data class FileMeta(
        val name: String,
        val size: Long?
    )

    private data class SelectedFile(
        val name: String,
        val bytes: ByteArray
    )

    private companion object {
        const val MAX_FILE_BYTES:
            Long = 10L * 1024L * 1024L
    }
}
