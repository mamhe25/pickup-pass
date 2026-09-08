package com.pickuppass.android.ui.schooladmin.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.BroadcastHistoryItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import com.pickuppass.android.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SchoolBroadcastUiState(
    val isSubmitting: Boolean = false,
    val isLoadingHistory: Boolean = true,
    val isRefreshingHistory: Boolean = false,
    val cancellingId: String? = null,
    val error: String? = null,
    val errorTitle: String? = null,
    val successMessage: String? = null,
    val successTitle: String? = null,
    val includeTeachers: Boolean = true,
    val includeParents: Boolean = true,
    val schedulingEnabled: Boolean = true,
    val history: List<BroadcastHistoryItem> = emptyList(),
    val composerResetToken: Long = 0
)

@HiltViewModel
class SchoolBroadcastViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchoolBroadcastUiState())
    val uiState: StateFlow<SchoolBroadcastUiState> = _uiState

    private var historyRequestInProgress = false

    init {
        loadEntitlements()
        refreshHistory(initial = true)
    }

    private fun loadEntitlements() {
        viewModelScope.launch {
            when (val result = schoolRepository.getEntitlements()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        schedulingEnabled =
                            result.data.features["scheduled_announcements"] != false
                    )
                }

                is ApiResult.Failure -> Unit
            }
        }
    }

    fun setIncludeTeachers(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            includeTeachers = value,
            error = null,
            errorTitle = null
        )
    }

    fun setIncludeParents(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            includeParents = value,
            error = null,
            errorTitle = null
        )
    }

    fun send(title: String, body: String) {
        val audience = audienceOrError(title, body) ?: return
        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                error = null,
                errorTitle = null,
                successMessage = null,
                successTitle = null
            )

            when (
                val result = schoolAdminRepository.broadcastToSchool(
                    title.trim(),
                    body.trim(),
                    audience
                )
            ) {
                is ApiResult.Success -> {
                    val count = result.data
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successTitle = "Announcement sent",
                        successMessage =
                            "Delivered to " + count + " " +
                                if (count == 1) "recipient." else "recipients.",
                        composerResetToken =
                            _uiState.value.composerResetToken + 1
                    )
                    refreshHistory(initial = false)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorTitle = "Announcement not sent",
                        error = result.message
                    )
                }
            }
        }
    }

    fun schedule(
        title: String,
        body: String,
        scheduledAtUtc: String?
    ) {
        if (!_uiState.value.schedulingEnabled) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Scheduling unavailable",
                error =
                    "Scheduled announcements are not enabled for this school plan."
            )
            return
        }

        val audience = audienceOrError(title, body) ?: return
        val scheduleInstant = validateScheduleInstant(scheduledAtUtc) ?: return

        if (_uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                error = null,
                errorTitle = null,
                successMessage = null,
                successTitle = null
            )

            when (
                val result = schoolAdminRepository.scheduleBroadcast(
                    title.trim(),
                    body.trim(),
                    audience,
                    scheduleInstant.toString()
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successTitle = "Announcement scheduled",
                        successMessage =
                            "The announcement is queued for the selected delivery time.",
                        composerResetToken =
                            _uiState.value.composerResetToken + 1
                    )
                    refreshHistory(initial = false)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorTitle = "Announcement not scheduled",
                        error = result.message
                    )
                }
            }
        }
    }

    fun cancel(item: BroadcastHistoryItem) {
        if (_uiState.value.cancellingId != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                cancellingId = item.id,
                error = null,
                errorTitle = null,
                successMessage = null,
                successTitle = null
            )

            when (
                val result =
                    schoolAdminRepository.cancelScheduledBroadcast(item.id)
            ) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        cancellingId = null,
                        successTitle = "Announcement cancelled",
                        successMessage =
                            "The scheduled announcement will no longer be delivered."
                    )
                    refreshHistory(initial = false)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        cancellingId = null,
                        errorTitle = "Cancellation not completed",
                        error = result.message
                    )
                }
            }
        }
    }

    fun refreshHistory() {
        refreshHistory(initial = false)
    }

    private fun refreshHistory(initial: Boolean) {
        if (historyRequestInProgress) return
        historyRequestInProgress = true

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingHistory = initial,
                isRefreshingHistory = !initial
            )

            try {
                when (
                    val result =
                        schoolAdminRepository.getBroadcastHistory()
                ) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingHistory = false,
                            isRefreshingHistory = false,
                            history = result.data
                        )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingHistory = false,
                            isRefreshingHistory = false,
                            errorTitle = "History not refreshed",
                            error = result.message
                        )
                    }
                }
            } finally {
                historyRequestInProgress = false
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            errorTitle = null,
            successMessage = null,
            successTitle = null
        )
    }

    private fun audienceOrError(
        title: String,
        body: String
    ): List<String>? {
        val audience = buildList {
            if (_uiState.value.includeTeachers) add("teacher")
            if (_uiState.value.includeParents) add("parent")
        }

        if (title.isBlank() || body.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Announcement incomplete",
                error = "Enter both a title and message."
            )
            return null
        }

        if (title.trim().length > 120) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Title too long",
                error = "The title must be 120 characters or fewer."
            )
            return null
        }

        if (body.trim().length > 2000) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Message too long",
                error = "The message must be 2,000 characters or fewer."
            )
            return null
        }

        if (audience.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Audience required",
                error = "Select at least one audience."
            )
            return null
        }

        return audience
    }

    private fun validateScheduleInstant(
        scheduledAtUtc: String?
    ): Instant? {
        if (scheduledAtUtc.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Delivery time required",
                error = "Choose a date and time for the announcement."
            )
            return null
        }

        val scheduled = runCatching {
            Instant.parse(scheduledAtUtc)
        }.getOrNull()

        if (scheduled == null) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Invalid delivery time",
                error = "Choose a valid date and time."
            )
            return null
        }

        val now = Instant.now()
        if (scheduled.isBefore(now.plus(Duration.ofSeconds(30)))) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Delivery time too soon",
                error =
                    "Schedule the announcement at least 30 seconds in the future."
            )
            return null
        }

        if (scheduled.isAfter(now.plus(Duration.ofDays(90)))) {
            _uiState.value = _uiState.value.copy(
                errorTitle = "Delivery time too far ahead",
                error =
                    "Announcements can be scheduled up to 90 days ahead."
            )
            return null
        }

        return scheduled
    }
}
