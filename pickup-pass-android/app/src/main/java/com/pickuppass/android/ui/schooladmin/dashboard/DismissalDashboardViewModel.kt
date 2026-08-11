package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.DismissalDashboardResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DismissalDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val dashboard: DismissalDashboardResponse? = null,
    val showRemaining: Boolean = false
)

@HiltViewModel
class DismissalDashboardViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DismissalDashboardUiState())
    val uiState: StateFlow<DismissalDashboardUiState> = _uiState

    init {
        load(initial = true)
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                load(initial = false, quiet = true)
            }
        }
    }

    fun refresh() = load(initial = false)

    fun showReleased() {
        _uiState.value = _uiState.value.copy(showRemaining = false)
    }

    fun showRemaining() {
        _uiState.value = _uiState.value.copy(showRemaining = true)
    }

    private fun load(initial: Boolean, quiet: Boolean = false) {
        viewModelScope.launch {
            if (initial) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else if (!quiet) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            }

            when (val result = repository.getDismissalDashboard()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        dashboard = result.data
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
        }
    }
}
