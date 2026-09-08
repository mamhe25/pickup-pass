package com.pickuppass.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.pickuppass.android.session.SessionGuardViewModel
import kotlinx.coroutines.launch

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

    val guardedRefresh: () -> Unit = {
        if (enabled) {
            scope.launch {
                val stillAuthorized = sessionGuard.validateNow()
                if (stillAuthorized) {
                    onRefresh()
                }
            }
        }
    }

    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = guardedRefresh
    )

    Box(
        modifier = modifier.pullRefresh(
            state = state,
            enabled = enabled
        )
    ) {
        content()

        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}
