package com.pickuppass.android.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionEndReason {
    EXPIRED_OR_REVOKED,
    ACCOUNT_DISABLED,
    UNAUTHORIZED
}

@Singleton
class SessionExpiryManager @Inject constructor() {
    private val _events = MutableSharedFlow<SessionEndReason>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionEndReason> = _events

    private val pendingReason = AtomicReference<SessionEndReason?>(null)

    fun notifySessionEnded(reason: SessionEndReason) {
        pendingReason.set(reason)
        _events.tryEmit(reason)
    }

    fun consumePendingReason(): SessionEndReason? = pendingReason.getAndSet(null)
}
