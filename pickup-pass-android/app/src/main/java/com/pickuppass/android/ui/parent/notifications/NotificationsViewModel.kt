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
    val isUpdating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
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

    private var loadInProgress = false

    init {
        load()
    }

    fun load() {
        if (loadInProgress) return
        loadInProgress = true

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = _uiState.value.notifications.isEmpty(),
                    error = null
                )

                val uid = authRepository.currentUid()
                if (uid == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Session expired — please sign in again"
                    )
                    return@launch
                }

                notificationRepository.getMyNotifications(uid)
                    .onSuccess { notifications ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            notifications = notifications,
                            error = null
                        )
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Couldn't load notifications"
                        )
                    }
            } finally {
                loadInProgress = false
            }
        }
    }

    fun markAsRead(notification: NotificationItem) {
        if (notification.read || _uiState.value.isUpdating) return

        val previous = _uiState.value.notifications
        _uiState.value = _uiState.value.copy(
            isUpdating = true,
            error = null,
            message = null,
            notifications = previous.map {
                if (it.id == notification.id) it.copy(read = true) else it
            }
        )

        viewModelScope.launch {
            notificationRepository.markAsRead(notification.id)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isUpdating = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        notifications = previous,
                        error = "Couldn't mark that notification as read"
                    )
                }
        }
    }

    fun markAllAsRead() {
        if (_uiState.value.isUpdating) return

        val previous = _uiState.value.notifications
        val unreadIds = previous.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            isUpdating = true,
            error = null,
            message = null,
            notifications = previous.map { it.copy(read = true) }
        )

        viewModelScope.launch {
            notificationRepository.markAllAsRead(unreadIds)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        message = "All notifications marked as read"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        notifications = previous,
                        error = "Couldn't mark all notifications as read"
                    )
                }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(error = null, message = null)
    }
}
