package com.pickuppass.android.ui.schooladmin.staffgates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.StaffPickupGateAssignment
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StaffPickupGatesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val staff: List<StaffPickupGateAssignment> = emptyList(),
    val gates: List<PickupGateItem> = emptyList(),
    val busyUid: String? = null,
    val message: String? = null,
    val messageTitle: String? = null,
    val error: String? = null
)

@HiltViewModel
class StaffPickupGatesViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffPickupGatesUiState())
    val uiState: StateFlow<StaffPickupGatesUiState> = _uiState

    private var loadInProgress = false

    init {
        load()
    }

    fun load() {
        if (loadInProgress || _uiState.value.busyUid != null) return
        loadInProgress = true

        viewModelScope.launch {
            val initial = _uiState.value.staff.isEmpty() &&
                _uiState.value.gates.isEmpty()

            _uiState.value = _uiState.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null
            )

            try {
                when (val result = repository.getStaffPickupGateAssignments()) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            staff = result.data.staff,
                            gates = result.data.gates,
                            error = null
                        )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message
                        )
                    }
                }
            } finally {
                loadInProgress = false
            }
        }
    }

    fun save(
        staff: StaffPickupGateAssignment,
        gateIds: List<String>
    ) = viewModelScope.launch {
        if (_uiState.value.busyUid != null || loadInProgress) return@launch

        val normalizedGateIds = gateIds
            .filter { it.isNotBlank() }
            .distinct()

        _uiState.value = _uiState.value.copy(
            busyUid = staff.uid,
            error = null,
            message = null,
            messageTitle = null
        )

        when (
            val result = repository.updateStaffPickupGates(
                staff.uid,
                normalizedGateIds
            )
        ) {
            is ApiResult.Success -> {
                val displayName = staff.displayName
                    .ifBlank { staff.email }
                    .ifBlank { "Staff member" }

                _uiState.value = _uiState.value.copy(
                    staff = _uiState.value.staff.map {
                        if (it.uid == staff.uid) {
                            it.copy(
                                assignedPickupGateIds = normalizedGateIds,
                                allGates = normalizedGateIds.isEmpty(),
                                unavailableAssignedGateCount = 0
                            )
                        } else {
                            it
                        }
                    },
                    busyUid = null,
                    messageTitle = "Gate access updated",
                    message = if (normalizedGateIds.isEmpty()) {
                        displayName + " can now use all active pickup gates."
                    } else {
                        displayName + " is now restricted to " +
                            normalizedGateIds.size + " active pickup gate" +
                            if (normalizedGateIds.size == 1) "." else "s."
                    }
                )
            }

            is ApiResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    busyUid = null,
                    error = result.message
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            message = null,
            messageTitle = null
        )
    }
}
