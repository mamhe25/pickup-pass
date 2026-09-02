package com.pickuppass.android.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onHowItWorks: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                WindowInsets.safeDrawing
                    .asPaddingValues()
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = Spacing.lg,
                    vertical = Spacing.md
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Spacer(
                Modifier.height(Spacing.lg)
            )

            BrandMark()

            Spacer(
                Modifier.height(Spacing.md)
            )

            Text(
                text = "PickupPass",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color =
                    MaterialTheme.colorScheme.onBackground
            )

            Spacer(
                Modifier.height(Spacing.xs)
            )

            Text(
                text =
                    "Secure school pickup, from gate to handoff.",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color =
                    MaterialTheme.colorScheme.onBackground
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            Text(
                text =
                    "A focused pickup workflow for parents, guardians, teachers, and school staff.",
                style =
                    MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                Modifier.height(Spacing.xl)
            )

            PickupFlowCard()

            Spacer(
                Modifier.height(Spacing.lg)
            )

            PrimaryButton(
                text = "Sign in",
                onClick = onSignIn
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            OutlinedButton(
                onClick = onHowItWorks,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape =
                    MaterialTheme.shapes.small
            ) {
                Text("How PickupPass works")
            }

            Spacer(
                Modifier.height(Spacing.lg)
            )

            AccessHelpCard()

            Spacer(
                Modifier.height(Spacing.lg)
            )

            Text(
                text =
                    "Your school controls account access and pickup permissions.",
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(
                Modifier.height(Spacing.md)
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Surface(
        modifier = Modifier.size(78.dp),
        shape = CircleShape,
        color =
            MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 5.dp
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
                    Modifier.size(39.dp),
                tint =
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PickupFlowCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(34.dp),
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
                                Icons.Filled.School,
                            contentDescription = null,
                            modifier =
                                Modifier.size(18.dp),
                            tint =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(
                    Modifier.width(Spacing.sm)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "One secure dismissal flow",
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Text(
                        text =
                            "Designed around the actual handoff.",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(
                Modifier.height(Spacing.md)
            )

            FlowStep(
                number = "1",
                icon =
                    Icons.Filled.QrCode2,
                title = "Present",
                detail =
                    "Open the latest pickup pass."
            )

            FlowConnector()

            FlowStep(
                number = "2",
                icon =
                    Icons.Filled.VerifiedUser,
                title = "Verify",
                detail =
                    "Confirm the authorized guardian."
            )

            FlowConnector()

            FlowStep(
                number = "3",
                icon =
                    Icons.Filled.CheckCircle,
                title = "Release",
                detail =
                    "Approve and record the handoff."
            )
        }
    }
}

@Composable
private fun FlowStep(
    number: String,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Surface(
            modifier =
                Modifier.size(42.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier =
                        Modifier.size(20.dp),
                    tint =
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(
            Modifier.width(Spacing.md)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                text = "$number · $title",
                style =
                    MaterialTheme.typography.bodyMedium,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Text(
                text = detail,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FlowConnector() {
    Box(
        modifier = Modifier
            .padding(start = 20.dp)
            .height(12.dp)
            .width(2.dp)
    ) {
        Surface(
            modifier =
                Modifier.fillMaxSize(),
            color =
                MaterialTheme.colorScheme.outline
                    .copy(alpha = 0.55f)
        ) {}
    }
}

@Composable
private fun AccessHelpCard() {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier =
                Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.Top
        ) {
            Icon(
                imageVector =
                    Icons.Filled.Shield,
                contentDescription = null,
                modifier =
                    Modifier.size(20.dp),
                tint =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(
                Modifier.width(Spacing.sm)
            )

            Column {
                Text(
                    text = "Need access?",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    fontWeight =
                        FontWeight.ExtraBold
                )

                Spacer(
                    Modifier.height(2.dp)
                )

                Text(
                    text =
                        "PickupPass accounts are provided through your school. Contact your school administrator if you have not received access.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
