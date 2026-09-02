package com.pickuppass.android.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pickuppass.android.ui.theme.Spacing

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val message: String,
    val icon: ImageVector,
    val callout: String
)

private val onboardingPages =
    listOf(
        OnboardingPage(
            eyebrow = "STEP 1 · PRESENT",
            title =
                "The latest pickup pass starts the handoff.",
            message =
                "Parents and authorized guardians present the current student pickup pass when they arrive.",
            icon =
                Icons.Filled.QrCode2,
            callout =
                "Pickup credentials are designed to be short-lived instead of permanent reusable cards."
        ),
        OnboardingPage(
            eyebrow = "STEP 2 · VERIFY",
            title =
                "A valid pass never replaces identity verification.",
            message =
                "Staff confirm the person at the gate against the authorized guardian before release.",
            icon =
                Icons.Filled.VerifiedUser,
            callout =
                "The guardian identity check remains visible in the staff workflow before approval."
        ),
        OnboardingPage(
            eyebrow = "STEP 3 · RELEASE",
            title =
                "Approve the handoff and keep the record.",
            message =
                "Once verification is complete, staff approve the release and PickupPass records the completed handoff.",
            icon =
                Icons.Filled.CheckCircle,
            callout =
                "Dismissal history preserves the student, guardian, approving staff member, and recorded time."
        )
    )

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onFinished: () -> Unit
) {
    var pageIndex by remember {
        mutableIntStateOf(0)
    }

    val page =
        onboardingPages[pageIndex]

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        WindowInsets.statusBars
                            .asPaddingValues()
                    )
                    .padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.xs
                    ),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Surface(
                        modifier =
                            Modifier.size(32.dp),
                        shape = CircleShape,
                        color =
                            MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.Shield,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(17.dp),
                                tint =
                                    MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Text(
                        text = "PickupPass",
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )
                }

                TextButton(
                    onClick = onSkip,
                    modifier =
                        Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Skip")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(
                    horizontal = Spacing.lg
                )
                .fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Spacer(
                Modifier.weight(0.35f)
            )

            OnboardingIllustration(
                icon = page.icon,
                step =
                    pageIndex + 1
            )

            Spacer(
                Modifier.height(Spacing.xl)
            )

            Text(
                text = page.eyebrow,
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight =
                    FontWeight.ExtraBold,
                color =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            Text(
                text = page.title,
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.ExtraBold,
                textAlign =
                    TextAlign.Center,
                color =
                    MaterialTheme.colorScheme.onBackground
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            Text(
                text = page.message,
                style =
                    MaterialTheme.typography.bodyMedium,
                textAlign =
                    TextAlign.Center,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                Modifier.height(Spacing.lg)
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    MaterialTheme.shapes.large,
                color =
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = page.callout,
                    modifier =
                        Modifier.padding(Spacing.md),
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign =
                        TextAlign.Center
                )
            }

            Spacer(
                Modifier.weight(1f)
            )

            PageIndicator(
                selectedIndex =
                    pageIndex,
                pageCount =
                    onboardingPages.size
            )

            Spacer(
                Modifier.height(Spacing.lg)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                if (pageIndex > 0) {
                    OutlinedButton(
                        onClick = {
                            pageIndex -= 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        shape =
                            MaterialTheme.shapes.small
                    ) {
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        if (
                            pageIndex ==
                            onboardingPages.lastIndex
                        ) {
                            onFinished()
                        } else {
                            pageIndex += 1
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp),
                    shape =
                        MaterialTheme.shapes.small
                ) {
                    Text(
                        if (
                            pageIndex ==
                            onboardingPages.lastIndex
                        ) {
                            "Get started"
                        } else {
                            "Next"
                        }
                    )
                }
            }

            Spacer(
                Modifier.height(Spacing.lg)
            )
        }
    }
}

@Composable
private fun OnboardingIllustration(
    icon: ImageVector,
    step: Int
) {
    Box(
        contentAlignment =
            Alignment.Center
    ) {
        Surface(
            modifier =
                Modifier.size(176.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {}

        Surface(
            modifier =
                Modifier.size(118.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primary
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier =
                        Modifier.size(54.dp),
                    tint =
                        MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(
                    Alignment.BottomEnd
                )
                .size(42.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight =
                        FontWeight.ExtraBold,
                    color =
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    selectedIndex: Int,
    pageCount: Int
) {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(7.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        repeat(pageCount) {
                index ->
            Surface(
                modifier = Modifier
                    .width(
                        if (
                            index ==
                            selectedIndex
                        ) {
                            26.dp
                        } else {
                            8.dp
                        }
                    )
                    .height(8.dp),
                shape = CircleShape,
                color =
                    if (
                        index ==
                        selectedIndex
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
            ) {}
        }
    }
}
