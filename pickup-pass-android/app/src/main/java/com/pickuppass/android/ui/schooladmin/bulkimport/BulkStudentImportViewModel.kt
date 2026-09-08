package com.pickuppass.android.ui.schooladmin.bulkimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.BulkStudentImportResponse
import com.pickuppass.android.data.model.GradeSection
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
    val importFinished: Boolean = false,
    val preview: BulkStudentImportResponse? = null,
    val structureLoading: Boolean = true,
    val currentAcademicYearName: String = "",
    val activeSections: List<GradeSection> = emptyList(),
    val placementMappings: Map<String, String> = emptyMap(),
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

    init {
        loadAcademicStructure()
    }

    fun loadAcademicStructure() {
        if (_uiState.value.isWorking) return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    structureLoading = true,
                    error = null,
                    errorTitle = null
                )

            when (val result = repository.getAcademicStructure()) {
                is ApiResult.Success -> {
                    val current = result.data.currentAcademicYear
                    val activeSections =
                        if (current == null) {
                            emptyList()
                        } else {
                            result.data.gradeSections
                                .filter {
                                    it.active &&
                                        it.academicYearId ==
                                            current.id
                                }
                                .sortedWith(
                                    compareBy<GradeSection>(
                                        { it.gradeLevel.lowercase() },
                                        { it.sectionName.lowercase() }
                                    )
                                )
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            structureLoading = false,
                            currentAcademicYearName =
                                current?.name.orEmpty(),
                            activeSections = activeSections
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            structureLoading = false,
                            currentAcademicYearName = "",
                            activeSections = emptyList(),
                            errorTitle =
                                "Academic structure unavailable",
                            error = result.message
                        )
                }
            }
        }
    }

    fun selectFile(
        context: Context,
        uri: Uri
    ) {
        if (_uiState.value.isWorking) return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    filename = "",
                    isWorking = true,
                    workingMessage = "Reading roster…",
                    importFinished = false,
                    preview = null,
                    placementMappings = emptyMap(),
                    error = null,
                    errorTitle = null,
                    success = null,
                    successTitle = null
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
                selectedFilename = selected.name

                previewSelectedFile()
            } catch (e: Exception) {
                selectedBytes = null
                selectedFilename = ""

                _uiState.value =
                    _uiState.value.copy(
                        filename = "",
                        isWorking = false,
                        workingMessage = "",
                        preview = null,
                        placementMappings = emptyMap(),
                        errorTitle =
                            "Roster could not be opened",
                        error =
                            e.message
                                ?: "Could not read the selected file"
                    )
            }
        }
    }

    fun setPlacementMapping(
        sourceKey: String,
        gradeSectionId: String
    ) {
        if (_uiState.value.isWorking) return

        _uiState.value =
            _uiState.value.copy(
                placementMappings =
                    _uiState.value.placementMappings +
                        (sourceKey to gradeSectionId),
                error = null,
                errorTitle = null
            )
    }

    fun applyPlacementMappings() {
        val preview = _uiState.value.preview ?: return
        val unresolved = preview.placementIssues

        if (unresolved.isEmpty()) return

        val missing =
            unresolved.any {
                _uiState.value
                    .placementMappings[it.key]
                    .isNullOrBlank()
            }

        if (missing) {
            _uiState.value =
                _uiState.value.copy(
                    errorTitle = "Mapping incomplete",
                    error =
                        "Map every unresolved Grade → Section value before revalidating the roster."
                )
            return
        }

        retryValidation()
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
            preview.placementIssues.isNotEmpty() ||
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
                        bytes = bytes,
                        filename = selectedFilename,
                        dryRun = false,
                        placementMappings =
                            _uiState.value.placementMappings
                    )
            ) {
                is ApiResult.Success -> {
                    val body = result.data

                    when {
                        body.invalidRows > 0 ||
                            body.placementIssues.isNotEmpty() -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isWorking = false,
                                    workingMessage = "",
                                    importFinished = false,
                                    preview = body,
                                    errorTitle =
                                        "Final validation blocked import",
                                    error =
                                        "No students were written because the roster no longer matches the current Academic Structure. Review the updated validation result and map any unresolved placement before trying again."
                                )
                        }

                        body.importedRows > 0 -> {
                            selectedBytes = null
                            selectedFilename = ""

                            _uiState.value =
                                _uiState.value.copy(
                                    isWorking = false,
                                    workingMessage = "",
                                    importFinished = true,
                                    preview = body,
                                    successTitle =
                                        "Student import completed",
                                    success =
                                        buildImportSuccessMessage(
                                            body
                                        )
                                )
                        }

                        body.validRows == 0 &&
                            body.duplicateRows > 0 -> {
                            selectedBytes = null
                            selectedFilename = ""

                            _uiState.value =
                                _uiState.value.copy(
                                    isWorking = false,
                                    workingMessage = "",
                                    importFinished = true,
                                    preview = body,
                                    successTitle =
                                        "No new students to import",
                                    success =
                                        "The roster was revalidated, but every valid row is now a duplicate. No new student records were created."
                                )
                        }

                        else -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isWorking = false,
                                    workingMessage = "",
                                    importFinished = false,
                                    preview = body,
                                    errorTitle =
                                        "Import result could not be confirmed",
                                    error =
                                        "PickupPass did not receive a confirmed student write. Review the roster and validate it again."
                                )
                        }
                    }
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

        _uiState.value =
            _uiState.value.copy(
                filename = "",
                isWorking = false,
                workingMessage = "",
                importFinished = false,
                preview = null,
                placementMappings = emptyMap(),
                error = null,
                errorTitle = null,
                success = null,
                successTitle = null
            )
    }

    fun buildTemplateCsv(): String {
        val sections = _uiState.value.activeSections

        return buildString {
            appendLine(
                "studentNumber,lastName,firstName,middleInitial,suffix,grade,section"
            )

            sections.forEach { section ->
                append(",,,,,")
                append(csvEscape(section.gradeLevel))
                append(",")
                append(csvEscape(section.sectionName))
                appendLine()
            }
        }
    }

    fun templateFilename(): String {
        val raw =
            _uiState.value.currentAcademicYearName
                .ifBlank { "current-year" }

        val safe =
            raw.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "current-year" }

        return "pickuppass-roster-template-" +
            safe +
            ".csv"
    }

    fun templateSaved() {
        _uiState.value =
            _uiState.value.copy(
                successTitle = "Roster template saved",
                success =
                    "The CSV template uses the active Grade → Section combinations from the current Academic Structure.",
                error = null,
                errorTitle = null
            )
    }

    fun templateSaveFailed(message: String) {
        _uiState.value =
            _uiState.value.copy(
                errorTitle = "Template not saved",
                error = message
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

    private suspend fun previewSelectedFile() {
        val bytes = selectedBytes ?: return

        _uiState.value =
            _uiState.value.copy(
                filename = selectedFilename,
                isWorking = true,
                workingMessage =
                    "Validating roster against Academic Structure…",
                preview = null,
                error = null,
                errorTitle = null,
                success = null,
                successTitle = null
            )

        when (
            val result =
                repository.importStudents(
                    bytes = bytes,
                    filename = selectedFilename,
                    dryRun = true,
                    placementMappings =
                        _uiState.value.placementMappings
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
                " imported into the current Academic Structure."

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

    private fun csvEscape(value: String): String {
        val escaped = value.replace(""", """")
        return if (
            escaped.contains(",") ||
            escaped.contains(""") ||
            escaped.contains("\n") ||
            escaped.contains("\r")
        ) {
            """ + escaped + """
        } else {
            escaped
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
