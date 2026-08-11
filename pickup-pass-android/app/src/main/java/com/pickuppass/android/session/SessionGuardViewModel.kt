package com.pickuppass.android.session

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionGuardViewModel @Inject constructor(
    val sessionExpiryManager: SessionExpiryManager
) : ViewModel()
