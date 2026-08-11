package com.pickuppass.android.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed class SplashDestination {
    data object Loading : SplashDestination()
    data object Login : SplashDestination()
    data object ParentHome : SplashDestination()
    data object TeacherHome : SplashDestination()
    data object SchoolAdminHome : SplashDestination()
    data object Offline : SplashDestination()
    data object ServiceUnavailable : SplashDestination()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val api: PickupPassApi
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination

    init { checkSession() }

    fun checkSession() {
        _destination.value = SplashDestination.Loading
        viewModelScope.launch {
            if (!authRepository.isSignedIn) {
                _destination.value = SplashDestination.Login
                return@launch
            }

            val session = authRepository.currentSession(forceRefresh = false)
            if (session == null) {
                _destination.value = if (authRepository.isSignedIn) {
                    SplashDestination.Offline
                } else {
                    SplashDestination.Login
                }
                return@launch
            }

            // Server-side validation is required because Firebase may still
            // have a locally cached user after an administrator revoked tokens.
            try {
                val response = api.sessionMe()
                if (!response.isSuccessful) {
                    _destination.value = when (response.code()) {
                        401 -> SplashDestination.Login
                        in 500..599 -> SplashDestination.ServiceUnavailable
                        else -> SplashDestination.Login
                    }
                    return@launch
                }
            } catch (_: IOException) {
                _destination.value = SplashDestination.Offline
                return@launch
            } catch (_: Exception) {
                _destination.value = SplashDestination.ServiceUnavailable
                return@launch
            }

            notificationRepository.registerCurrentDeviceTokenInBackground()
            _destination.value = when (session.role) {
                UserRole.Parent -> SplashDestination.ParentHome
                UserRole.Teacher -> SplashDestination.TeacherHome
                UserRole.SchoolAdmin -> SplashDestination.SchoolAdminHome
                else -> SplashDestination.Login
            }
        }
    }
}
