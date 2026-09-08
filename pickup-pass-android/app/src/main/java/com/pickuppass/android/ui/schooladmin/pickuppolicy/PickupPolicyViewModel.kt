package com.pickuppass.android.ui.schooladmin.pickuppolicy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PickupPolicyUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val restrictedToTimeWindow: Boolean = false,
    val startTime: String = "14:00",
    val endTime: String = "18:00",
    val allowManualOverride: Boolean = true,
    val timeZone: String = "Asia/Manila",
    val isDirty: Boolean = false,
    val error: String? = null,
    val errorTitle: String? = null,
    val successMessage: String? = null,
    val successTitle: String? = null
)

@HiltViewModel
class PickupPolicyViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickupPolicyUiState())
    val uiState: StateFlow<PickupPolicyUiState> = _uiState

    private var loadInProgress = false

    private var baselineRestricted = false
    private var baselineStartTime = "14:00"
    private var baselineEndTime = "18:00"
    private var baselineManualOverride = true

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    fun setRestricted(value: Boolean) {
        if (_uiState.value.isSaving || _uiState.value.isRefreshing) return

        _uiState.value = _uiState.value.copy(
            restrictedToTimeWindow = value,
            error = null,
            errorTitle = null,
            successMessage = null,
            successTitle = null
        )
        updateDirty()
    }

    fun setStartTime(value: String) {
        if (_uiState.value.isSaving || _uiState.value.isRefreshing) return

        _uiState.value = _uiState.value.copy(
            startTime = value,
            error = null,
            errorTitle = null,
            successMessage = null,
            successTitle = null
        )
        updateDirty()
    }

    fun setEndTime(value: String) {
        if (_uiState.value.isSaving || _uiState.value.isRefreshing) return

        _uiState.value = _uiState.value.copy(
            endTime = value,
            error = null,
            errorTitle = null,
            successMessage = null,
            successTitle = null
        )
        updateDirty()
    }

    fun setManualOverride(value: Boolean) {
        if (_uiState.value.isSaving || _uiState.value.isRefreshing) return

        _uiState.value = _uiState.value.copy(
            allowManualOverride = value,
            error = null,
            errorTitle = null,
            successMessage = null,
            successTitle = null
        )
        updateDirty()
    }

    fun save() {
        val current = _uiState.value
        if (current.isSaving || loadInProgress) return

        val validationError = validate(current)
        if (validationError != null) {
            _uiState.value = current.copy(
                errorTitle = validationError.first,
                error = validationError.second,
                successMessage = null,
                successTitle = null
            )
            return
        }

        viewModelScope.launch {
            val snapshot = _uiState.value
            val mode =
                if (snapshot.restrictedToTimeWindow) {
                    "time_window"
                } else {
                    "unrestricted"
                }

            _uiState.value = snapshot.copy(
                isSaving = true,
                error = null,
                errorTitle = null,
                successMessage = null,
                successTitle = null
            )

            when (
                val result = repository.updatePickupPolicy(
                    mode = mode,
                    earliestPickupTime =
                        snapshot.startTime.takeIf {
                            mode == "time_window"
                        },
                    latestPickupTime =
                        snapshot.endTime.takeIf {
                            mode == "time_window"
                        },
                    allowManualOverride =
                        snapshot.allowManualOverride
                )
            ) {
                is ApiResult.Success -> {
                    val policy = result.data

                    val restricted =
                        policy.mode == "time_window"
                    val start =
                        policy.earliestPickupTime
                            .ifBlank { snapshot.startTime }
                    val end =
                        policy.latestPickupTime
                            .ifBlank { snapshot.endTime }

                    baselineRestricted = restricted
                    baselineStartTime = start
                    baselineEndTime = end
                    baselineManualOverride =
                        policy.allowManualOverride

                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        restrictedToTimeWindow = restricted,
                        startTime = start,
                        endTime = end,
                        allowManualOverride =
                            policy.allowManualOverride,
                        timeZone = policy.timeZone,
                        isDirty = false,
                        error = null,
                        errorTitle = null,
                        successTitle = "Pickup policy updated",
                        successMessage =
                            if (restricted) {
                                "QR pickup is now limited to " +
                                    start +
                                    "–" +
                                    end +
                                    " (" +
                                    policy.timeZone +
                                    "). Manual override is " +
                                    if (
                                        policy.allowManualOverride
                                    ) {
                                        "available."
                                    } else {
                                        "disabled."
                                    }
                            } else {
                                "Valid QR pickup is unrestricted by school hours. Manual override is " +
                                    if (
                                        policy.allowManualOverride
                                    ) {
                                        "available."
                                    } else {
                                        "disabled."
                                    }
                            }
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorTitle = "Policy not saved",
                        error = result.message
                    )
                }
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

    private fun load(initial: Boolean) {
        if (loadInProgress || _uiState.value.isSaving) return
        loadInProgress = true

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null,
                errorTitle = null
            )

            try {
                when (val result = repository.getPickupPolicy()) {
                    is ApiResult.Success -> {
                        val policy = result.data

                        val restricted =
                            policy.mode == "time_window"
                        val start =
                            policy.earliestPickupTime
                                .ifBlank { "14:00" }
                        val end =
                            policy.latestPickupTime
                                .ifBlank { "18:00" }

                        baselineRestricted = restricted
                        baselineStartTime = start
                        baselineEndTime = end
                        baselineManualOverride =
                            policy.allowManualOverride

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            restrictedToTimeWindow = restricted,
                            startTime = start,
                            endTime = end,
                            allowManualOverride =
                                policy.allowManualOverride,
                            timeZone = policy.timeZone,
                            isDirty = false,
                            error = null,
                            errorTitle = null
                        )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorTitle =
                                if (initial) {
                                    "Pickup policy unavailable"
                                } else {
                                    "Policy not refreshed"
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

    private fun updateDirty() {
        val state = _uiState.value

        _uiState.value = state.copy(
            isDirty =
                state.restrictedToTimeWindow !=
                    baselineRestricted ||
                    state.startTime != baselineStartTime ||
                    state.endTime != baselineEndTime ||
                    state.allowManualOverride !=
                    baselineManualOverride
        )
    }

    private fun validate(
        state: PickupPolicyUiState
    ): Pair<String, String>? {
        if (!state.restrictedToTimeWindow) {
            return null
        }

        val start = parseTime(state.startTime)
        val end = parseTime(state.endTime)

        if (start == null || end == null) {
            return "Invalid dismissal time" to
                "Choose valid pickup start and end times."
        }

        if (!start.isBefore(end)) {
            return "Invalid dismissal window" to
                "Pickup start time must be earlier than the end time."
        }

        return null
    }

    private fun parseTime(value: String): LocalTime? {
        return try {
            LocalTime.parse(value, HH_MM)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private companion object {
        val HH_MM: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm")
    }
}
