package com.pickuppass.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pickuppass.android.navigation.NotificationNavigationRequest
import com.pickuppass.android.navigation.PickupPassNavHost
import com.pickuppass.android.notification.PickupPassMessagingService
import com.pickuppass.android.ui.theme.PickupPassTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity :
    ComponentActivity() {

    private var pendingNotificationNavigation by
        mutableStateOf<NotificationNavigationRequest?>(null)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        pendingNotificationNavigation =
            intent.toNotificationNavigationRequest()

        setContent {
            PickupPassTheme {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                ) {
                    PickupPassNavHost(
                        pendingNotificationNavigation =
                            pendingNotificationNavigation,
                        onNotificationNavigationConsumed = {
                            pendingNotificationNavigation = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        pendingNotificationNavigation =
            intent.toNotificationNavigationRequest()
                ?: pendingNotificationNavigation
    }

    private fun Intent.toNotificationNavigationRequest():
        NotificationNavigationRequest? {
        val type =
            getStringExtra(
                PickupPassMessagingService
                    .EXTRA_NOTIFICATION_TYPE
            )
                ?.trim()
                .orEmpty()

        if (type.isBlank()) return null

        return NotificationNavigationRequest(
            type = type,
            recipientRole =
                getStringExtra(
                    PickupPassMessagingService
                        .EXTRA_RECIPIENT_ROLE
                ).orEmpty(),
            schoolId =
                getStringExtra(
                    PickupPassMessagingService
                        .EXTRA_SCHOOL_ID
                ),
            studentId =
                getStringExtra(
                    PickupPassMessagingService
                        .EXTRA_STUDENT_ID
                )
        )
    }
}
