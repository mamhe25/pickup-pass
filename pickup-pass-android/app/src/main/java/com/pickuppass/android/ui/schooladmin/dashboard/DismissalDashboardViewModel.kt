package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.DismissalDashboardResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DismissalDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val dashboard: DismissalDashboardResponse? = null,
    val unreadNotifications: Int = 0,
    val showRemaining: Boolean = false
)

@HiltViewModel
class DismissalDashboardViewModel @Inject constructor(
    private val repository: SchoolAdminRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DismissalDashboardUiState())
    val uiState: StateFlow<DismissalDashboardUiState> = _uiState

    init {
        observeUnreadNotifications()
        load(initial = true)
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                load(initial = false, quiet = true)
            }
        }
    }

    private fun observeUnreadNotifications() {
        viewModelScope.launch {
            val uid =
                authRepository.currentUid()
                    ?: return@launch

            notificationRepository
                .observeUnreadCount(uid)
                .catch {
                    // Keep the last known count. Normal dashboard refreshes
                    // still provide a fallback aggregate read.
                }
                .collect { count ->
                    _uiState.value =
                        _uiState.value.copy(
                            unreadNotifications = count
                        )
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

            val unreadDeferred = async {
                authRepository.currentUid()
                    ?.let { uid ->
                        notificationRepository
                            .getUnreadCount(uid)
                            .getOrDefault(0)
                    }
                    ?: 0
            }

            when (val result = repository.getDismissalDashboard()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        dashboard = result.data,
                        unreadNotifications = unreadDeferred.await()
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message,
                        unreadNotifications = unreadDeferred.await()
                    )
                }
            }
        }
    }
}
