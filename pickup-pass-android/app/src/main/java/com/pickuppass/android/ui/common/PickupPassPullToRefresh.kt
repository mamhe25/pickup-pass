package com.pickuppass.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.pickuppass.android.session.SessionGuardViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PickupPassPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val sessionGuard: SessionGuardViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val latestRefreshing = rememberUpdatedState(refreshing)

    /*
     * Session validation happens before the screen starts its own refresh.
     * Keep the Material indicator in one continuous refreshing state during
     * that hand-off; otherwise it retracts after the pull gesture and appears
     * a second time when the screen finally publishes refreshing = true.
     */
    var validatingSession by remember {
        mutableStateOf(false)
    }

    val effectiveRefreshing =
        refreshing || validatingSession

    val guardedRefresh: () -> Unit = {
        if (
            enabled &&
            !validatingSession &&
            !latestRefreshing.value
        ) {
            validatingSession = true

            scope.launch {
                try {
                    val stillAuthorized =
                        sessionGuard.validateNow()

                    if (stillAuthorized) {
                        onRefresh()

                        /*
                         * Give the screen refresh state a chance to take over
                         * before releasing the local validation indicator.
                         * The timeout prevents a no-op refresh callback from
                         * leaving the indicator visible indefinitely.
                         */
                        withTimeoutOrNull(750L) {
                            snapshotFlow {
                                latestRefreshing.value
                            }.first { it }
                        }
                    }
                } finally {
                    validatingSession = false
                }
            }
        }
    }

    val state = rememberPullRefreshState(
        refreshing = effectiveRefreshing,
        onRefresh = guardedRefresh
    )

    Box(
        modifier = modifier.pullRefresh(
            state = state,
            enabled = enabled && !effectiveRefreshing
        )
    ) {
        content()

        PullRefreshIndicator(
            refreshing = effectiveRefreshing,
            state = state,
            modifier = Modifier.align(
                Alignment.TopCenter
            ),
            backgroundColor =
                MaterialTheme.colorScheme.surface,
            contentColor =
                MaterialTheme.colorScheme.primary
        )
    }
}
