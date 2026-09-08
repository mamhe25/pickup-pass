package com.pickuppass.android.ui.schooladmin.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.LaunchReadinessResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LaunchReadinessUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val savingAction: String? = null,
    val readiness: LaunchReadinessResponse? = null,
    val error: String? = null,
    val errorTitle: String? = null,
    val message: String? = null,
    val messageTitle: String? = null
)

@HiltViewModel
class LaunchReadinessViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(LaunchReadinessUiState())
    val uiState: StateFlow<LaunchReadinessUiState> =
        _uiState

    private var loadInProgress = false

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        if (loadInProgress || _uiState.value.saving) return

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
                        repository.getLaunchReadiness()
                ) {
                    is ApiResult.Success -> {
                        _uiState.value =
                            _uiState.value.copy(
                                loading = false,
                                refreshing = false,
                                readiness = result.data,
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
                                        "Launch readiness unavailable"
                                    } else {
                                        "Readiness not refreshed"
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

    fun setManualCheck(
        key: String,
        checked: Boolean
    ) {
        val current =
            _uiState.value.readiness
                ?: return

        if (
            _uiState.value.saving ||
            _uiState.value.refreshing ||
            loadInProgress
        ) {
            return
        }

        val next =
            current.manualChecks
                .toMutableMap()
                .apply {
                    put(key, checked)
                }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    saving = true,
                    savingAction = "manual:" + key,
                    error = null,
                    errorTitle = null,
                    message = null,
                    messageTitle = null
                )

            when (
                val result =
                    repository.updateLaunchManualChecks(
                        next
                    )
            ) {
                is ApiResult.Success -> {
                    val persisted =
                        result.data.manualChecks[key] == true

                    if (persisted != checked) {
                        _uiState.value =
                            _uiState.value.copy(
                                saving = false,
                                savingAction = null,
                                readiness = result.data,
                                errorTitle =
                                    "Launch check was not persisted",
                                error =
                                    "PickupPass received a successful response, but the saved readiness value did not match your selection. The change was not confirmed; refresh after the backend update is deployed."
                            )
                    } else {
                        _uiState.value =
                            _uiState.value.copy(
                                saving = false,
                                savingAction = null,
                                readiness = result.data,
                                messageTitle =
                                    "On-site check updated",
                                message =
                                    if (checked) {
                                        "The launch requirement is now confirmed. PickupPass recalculated readiness using the latest school configuration."
                                    } else {
                                        "The launch requirement was marked incomplete. PickupPass recalculated readiness and may now require attention before launch."
                                    }
                            )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            savingAction = null,
                            errorTitle =
                                "Launch check not updated",
                            error = result.message
                        )
                }
            }
        }
    }

    fun requestReview() {
        val readiness =
            _uiState.value.readiness
                ?: return

        if (
            _uiState.value.saving ||
            _uiState.value.refreshing ||
            loadInProgress
        ) {
            return
        }

        if (!readiness.readyForReview) {
            _uiState.value =
                _uiState.value.copy(
                    errorTitle =
                        "Review request blocked",
                    error =
                        "Complete every required launch check before requesting platform review."
                )
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    saving = true,
                    savingAction = "request-review",
                    error = null,
                    errorTitle = null,
                    message = null,
                    messageTitle = null
                )

            when (
                val result =
                    repository.requestLaunchReview()
            ) {
                is ApiResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            savingAction = null,
                            readiness = result.data,
                            messageTitle =
                                "Launch review requested",
                            message =
                                "The current school configuration was submitted to the PickupPass platform owner for production review. Continue monitoring readiness because later configuration changes can introduce new blockers."
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            savingAction = null,
                            errorTitle =
                                "Launch review not requested",
                            error = result.message
                        )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.value =
            _uiState.value.copy(
                error = null,
                errorTitle = null,
                message = null,
                messageTitle = null
            )
    }
}
