package com.pickuppass.android.ui.parent.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.NotificationItem
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val notifications: List<NotificationItem> = emptyList(),
) {
    val hasUnread: Boolean get() = notifications.any { !it.read }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val uid = authRepository.currentUid()
            if (uid == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Session expired — please sign in again")
                return@launch
            }

            notificationRepository.getMyNotifications(uid)
                .onSuccess { notifications ->
                    _uiState.value = _uiState.value.copy(isLoading = false, notifications = notifications)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load notifications")
                }
        }
    }

    fun markAsRead(notification: NotificationItem) {
        if (notification.read) return

        // Optimistic update so tapping feels instant; reconciled by load()
        // on the next real fetch if the write ever actually fails.
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map {
                if (it.id == notification.id) it.copy(read = true) else it
            }
        )

        viewModelScope.launch {
            notificationRepository.markAsRead(notification.id)
        }
    }

    fun markAllAsRead() {
        val unreadIds = _uiState.value.notifications.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map { it.copy(read = true) }
        )

        viewModelScope.launch {
            notificationRepository.markAllAsRead(unreadIds)
        }
    }
}
