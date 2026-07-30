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
    /**
     * Firebase Auth has a locally cached signed-in user (isSignedIn is
     * true — this check never needs the network), but verifying their role
     * claims failed, almost always because there's no connectivity right
     * now. Deliberately NOT the same as Login: bouncing straight to the
     * sign-in screen here would look like the person got signed out just
     * because of a WiFi blip, when Firebase still has them cached locally.
     * Offline shows a "no connection, try again" state with a retry button
     * instead, keeping the cached session intact.
     */
    data object Offline : SplashDestination()
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

    fun checkSession() {
        _destination.value = SplashDestination.Loading
        viewModelScope.launch {
            if (!authRepository.isSignedIn) {
                _destination.value = SplashDestination.Login
                return@launch
            }

            val session = authRepository.currentSession()

            if (session == null) {
                // isSignedIn was true but the session check still failed —
                // that combination almost always means "no connectivity,"
                // not "not signed in." A genuinely-invalid/expired session
                // that Firebase itself has rejected would already make
                // isSignedIn false, so this branch is specifically the
                // offline case.
                _destination.value = SplashDestination.Offline
                return@launch
            }

            // Best-effort: a failure here should never block routing to
            // the person's home screen.
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
