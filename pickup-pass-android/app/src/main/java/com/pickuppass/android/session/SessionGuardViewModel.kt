package com.pickuppass.android.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class SessionGuardViewModel @Inject constructor(
    val sessionExpiryManager: SessionExpiryManager,
    private val authRepository: AuthRepository,
    private val api: PickupPassApi
) : ViewModel() {

    private val validationMutex = Mutex()
    private var foregroundMonitor: Job? = null

    /**
     * Performs a lightweight backend session validation.
     *
     * A 401 is handled by AuthFailureInterceptor, which signs out the matching
     * Firebase session and emits the global session-ended event. Transient
     * network failures never force a logout.
     */
    suspend fun validateNow(): Boolean = validationMutex.withLock {
        if (!authRepository.isSignedIn) return@withLock true

        return@withLock try {
            val response = api.sessionMe()
            response.code() != 401 && authRepository.isSignedIn
        } catch (_: IOException) {
            true
        } catch (_: Exception) {
            // Session validation is defensive. Backend enforcement remains
            // authoritative when a real protected action is attempted.
            true
        }
    }

    fun onAppForegrounded() {
        if (foregroundMonitor?.isActive == true) return

        foregroundMonitor = viewModelScope.launch {
            while (isActive) {
                validateNow()
                delay(FOREGROUND_VALIDATION_INTERVAL_MS)
            }
        }
    }

    fun onAppBackgrounded() {
        foregroundMonitor?.cancel()
        foregroundMonitor = null
    }

    override fun onCleared() {
        foregroundMonitor?.cancel()
        super.onCleared()
    }

    private companion object {
        // FCM is the fast path. This polling interval is only a foreground
        // fallback for delayed/blocked push delivery.
        const val FOREGROUND_VALIDATION_INTERVAL_MS = 30_000L
    }
}
