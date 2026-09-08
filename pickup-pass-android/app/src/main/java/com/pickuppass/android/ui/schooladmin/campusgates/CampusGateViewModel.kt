package com.pickuppass.android.ui.schooladmin.campusgates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.CampusItem
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import com.pickuppass.android.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CampusGateUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val campuses: List<CampusItem> = emptyList(),
    val gates: List<PickupGateItem> = emptyList(),
    val multiCampusEnabled: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val messageTitle: String? = null
)

@HiltViewModel
class CampusGateViewModel @Inject constructor(
    private val repository: SchoolAdminRepository,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampusGateUiState())
    val uiState: StateFlow<CampusGateUiState> = _uiState

    private var refreshInProgress = false

    init {
        load()
    }

    fun load() {
        if (refreshInProgress || _uiState.value.saving) return

        viewModelScope.launch {
            refreshData(
                initial = _uiState.value.campuses.isEmpty() &&
                    _uiState.value.gates.isEmpty()
            )
        }
    }

    fun createCampus(name: String, address: String) {
        val campusName = name.trim()
        save(
            title = "Campus created",
            message = "$campusName is now available for dismissal operations."
        ) {
            repository.createCampus(campusName, address.trim())
        }
    }

    fun setCampus(id: String, name: String, active: Boolean) {
        save(
            title = if (active) "Campus activated" else "Campus deactivated",
            message = if (active) {
                "$name is active again. Its pickup gates must be reactivated individually if they were previously disabled."
            } else {
                "$name is no longer available for dismissal. Pickup gates assigned to this campus were also deactivated."
            }
        ) {
            repository.setCampusActive(id, active)
        }
    }

    fun createGate(
        campusId: String,
        name: String,
        description: String
    ) {
        val gateName = name.trim()
        save(
            title = "Pickup gate created",
            message = "$gateName is now available to dismissal staff."
        ) {
            repository.createPickupGate(
                campusId,
                gateName,
                description.trim()
            )
        }
    }

    fun setGate(id: String, displayName: String, active: Boolean) {
        save(
            title = if (active) "Pickup gate activated" else "Pickup gate deactivated",
            message = if (active) {
                "$displayName is now available in dismissal flows."
            } else {
                "$displayName is no longer selectable by dismissal staff."
            }
        ) {
            repository.setPickupGateActive(id, active)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            message = null,
            messageTitle = null
        )
    }

    private fun save(
        title: String,
        message: String,
        block: suspend () -> ApiResult<*>
    ) {
        if (_uiState.value.saving || refreshInProgress) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null,
                message = null,
                messageTitle = null
            )

            when (val result = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        message = message,
                        messageTitle = title
                    )
                    refreshData(initial = false)
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        error = result.message
                    )
                }
            }
        }
    }

    private suspend fun refreshData(initial: Boolean) {
        if (refreshInProgress) return
        refreshInProgress = true

        _uiState.value = _uiState.value.copy(
            loading = initial,
            refreshing = !initial,
            error = null
        )

        try {
            val featureResult = schoolRepository.getEntitlements()
            val multiCampusEnabled = when (featureResult) {
                is ApiResult.Success ->
                    featureResult.data.features["multi_campus"] != false
                is ApiResult.Failure ->
                    _uiState.value.multiCampusEnabled
            }

            when (val result = repository.getCampusGates()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        campuses = result.data.campuses,
                        gates = result.data.gates,
                        multiCampusEnabled = multiCampusEnabled
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = result.message,
                        multiCampusEnabled = multiCampusEnabled
                    )
                }
            }
        } finally {
            refreshInProgress = false
        }
    }
}
