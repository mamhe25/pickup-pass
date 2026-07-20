package com.pickuppass.android.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SplashDestination {
    data object Loading : SplashDestination()
    data object Login : SplashDestination()
    data object ParentHome : SplashDestination()
    data object TeacherHome : SplashDestination()
    data object SchoolAdminHome : SplashDestination()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            if (!authRepository.isSignedIn) {
                _destination.value = SplashDestination.Login
                return@launch
            }
            val session = authRepository.currentSession()
            if (session != null) {
                notificationRepository.registerCurrentDeviceToken()
            }
            _destination.value = when (session?.role) {
                UserRole.Parent -> SplashDestination.ParentHome
                UserRole.Teacher -> SplashDestination.TeacherHome
                UserRole.SchoolAdmin -> SplashDestination.SchoolAdminHome
                else -> SplashDestination.Login
            }
        }
    }
}
