package com.pickuppass.android.ui.schooladmin.staffgates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.StaffPickupGateAssignment
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffPickupGatesUiState(
    val isLoading: Boolean = true,
    val staff: List<StaffPickupGateAssignment> = emptyList(),
    val gates: List<PickupGateItem> = emptyList(),
    val busyUid: String? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class StaffPickupGatesViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffPickupGatesUiState())
    val uiState: StateFlow<StaffPickupGatesUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = repository.getStaffPickupGateAssignments()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false,
                staff = result.data.staff,
                gates = result.data.gates,
                error = null
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
        }
    }

    fun save(uid: String, gateIds: List<String>) = viewModelScope.launch {
        if (_uiState.value.busyUid != null) return@launch
        _uiState.value = _uiState.value.copy(busyUid = uid, error = null, message = null)
        when (val result = repository.updateStaffPickupGates(uid, gateIds)) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    staff = _uiState.value.staff.map {
                        if (it.uid == uid) it.copy(assignedPickupGateIds = gateIds, allGates = gateIds.isEmpty()) else it
                    },
                    busyUid = null,
                    message = if (gateIds.isEmpty()) "Staff can use all active pickup gates" else "Pickup-gate assignment saved"
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(busyUid = null, error = result.message)
        }
    }
}
