package com.pickuppass.android.ui.onboarding

import android.content.Context

object OnboardingPreferences {
    private const val PREFERENCES_NAME =
        "pickuppass_onboarding"

    private const val KEY_HAS_SEEN_ONBOARDING =
        "has_seen_onboarding_v1"

    fun hasSeenOnboarding(
        context: Context
    ): Boolean =
        context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_HAS_SEEN_ONBOARDING,
                false
            )

    fun markOnboardingSeen(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_HAS_SEEN_ONBOARDING,
                true
            )
            .apply()
    }
}
