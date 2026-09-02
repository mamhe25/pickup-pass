package com.pickuppass.android.ui.teacher.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.GradeSection
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherOperationsScreen(
    viewModel:
        TeacherOperationsViewModel =
        hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    val expandedGrades =
        remember {
            mutableStateMapOf<
                String,
                Boolean
            >()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text =
                                "Pickup Operations",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Text(
                            text =
                                "Staff reference",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons
                                    .AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick =
                            viewModel::refresh
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Refresh,
                            contentDescription =
                                "Refresh"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.xl
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            item {
                OperationsSummary(
                    uiState =
                        uiState
                )
            }

            item {
                GateAccessSection(
                    loading =
                        uiState
                            .gatesLoading,
                    gates =
                        uiState.gates,
                    error =
                        uiState
                            .gatesError,
                    onRetry =
                        viewModel::loadGates
                )
            }

            item {
                AcademicStructureSection(
                    loading =
                        uiState
                            .structureLoading,
                    sections =
                        uiState
                            .structure
                            ?.gradeSections
                            .orEmpty(),
                    error =
                        uiState
                            .structureError,
                    expandedGrades =
                        expandedGrades,
                    onRetry =
                        viewModel
                            ::loadStructure
                )
            }

            item {
                EntitlementsSection(
                    loading =
                        uiState
                            .entitlementsLoading,
                    entitlements =
                        uiState
                            .entitlements,
                    error =
                        uiState
                            .entitlementsError,
                    onRetry =
                        viewModel
                            ::loadEntitlements
                )
            }
        }
    }
}

@Composable
private fun OperationsSummary(
    uiState:
        TeacherOperationsUiState
) {
    val activeSections =
        uiState.structure
            ?.gradeSections
            ?.count {
                it.active
            }

    val academicYear =
        uiState.structure
            ?.currentAcademicYear
            ?.name
            ?.ifBlank {
                null
            }

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {
        SummaryMetric(
            label = "Gates",
            value =
                if (
                    uiState
                        .gatesLoading
                ) {
                    "—"
                } else if (
                    uiState
                        .gatesError != null
                ) {
                    "!"
                } else {
                    uiState
                        .gates
                        .size
                        .toString()
                },
            modifier =
                Modifier.weight(1f)
        )

        SummaryMetric(
            label = "Year",
            value =
                if (
                    uiState
                        .structureLoading
                ) {
                    "—"
                } else {
                    academicYear
                        ?: "Not set"
                },
            modifier =
                Modifier.weight(1.25f)
        )

        SummaryMetric(
            label = "Sections",
            value =
                if (
                    uiState
                        .structureLoading
                ) {
                    "—"
                } else if (
                    uiState
                        .structureError != null
                ) {
                    "!"
                } else {
                    activeSections
                        ?.toString()
                        ?: "0"
                },
            modifier =
                Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape =
            MaterialTheme.shapes.large,
        color =
            MaterialTheme
                .colorScheme
                .surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier =
                Modifier.padding(11.dp)
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                Modifier.height(3.dp)
            )

            Text(
                text = value,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
                fontWeight =
                    FontWeight.ExtraBold,
                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )
        }
    }
}

@Composable
private fun GateAccessSection(
    loading: Boolean,
    gates: List<PickupGateItem>,
    error: String?,
    onRetry: () -> Unit
) {
    val status =
        when {
            loading ->
                OperationStatus(
                    "Loading…",
                    StatusTone.Neutral
                )

            error != null ->
                OperationStatus(
                    "Unavailable",
                    StatusTone.Danger
                )

            gates.isEmpty() ->
                OperationStatus(
                    "No gate selection",
                    StatusTone.Info
                )

            gates.size == 1 ->
                OperationStatus(
                    "Automatic gate",
                    StatusTone.Success
                )

            else ->
                OperationStatus(
                    "Selection required",
                    StatusTone.Warning
                )
        }

    OperationsCard(
        kicker = "GATE ACCESS",
        title = "Pickup gate behavior",
        icon =
            Icons.Filled.LocationOn,
        status = status
    ) {
        Text(
            text =
                "Scanner gate selection adapts automatically to the active gates available to your account.",
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.height(Spacing.md)
        )

        when {
            loading -> {
                SectionLoading(
                    "Loading gate access…"
                )
            }

            error != null -> {
                SectionError(
                    message = error,
                    onRetry = onRetry
                )
            }

            gates.isEmpty() -> {
                InfoPanel(
                    title =
                        "No active gates configured",
                    message =
                        "The scanner works without asking you to select a gate."
                )
            }

            else -> {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        )
                ) {
                    gates.forEachIndexed {
                            index,
                            gate ->
                        GateRow(
                            index =
                                index + 1,
                            gate = gate,
                            automatic =
                                gates.size == 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GateRow(
    index: Int,
    gate: PickupGateItem,
    automatic: Boolean
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier =
                    Modifier.size(31.dp),
                shape = CircleShape,
                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text =
                            index.toString(),
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        fontWeight =
                            FontWeight.ExtraBold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer
                    )
                }
            }

            Spacer(
                Modifier.width(9.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        gate.name
                            .ifBlank {
                                "Gate"
                            },
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        gate.campusName
                            .ifBlank {
                                "Campus"
                            },
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                text =
                    if (automatic) {
                        "Auto"
                    } else {
                        "Selectable"
                    },
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )
        }
    }
}

@Composable
private fun AcademicStructureSection(
    loading: Boolean,
    sections: List<GradeSection>,
    error: String?,
    expandedGrades:
        MutableMap<String, Boolean>,
    onRetry: () -> Unit
) {
    val activeCount =
        sections.count {
            it.active
        }

    val status =
        when {
            loading ->
                OperationStatus(
                    "Loading…",
                    StatusTone.Neutral
                )

            error != null ->
                OperationStatus(
                    "Unavailable",
                    StatusTone.Danger
                )

            sections.isEmpty() ->
                OperationStatus(
                    "No sections",
                    StatusTone.Info
                )

            else ->
                OperationStatus(
                    "$activeCount active",
                    StatusTone.Success
                )
        }

    OperationsCard(
        kicker =
            "ACADEMIC STRUCTURE",
        title =
            "Grades and sections",
        icon =
            Icons.Filled.School,
        status = status
    ) {
        Text(
            text =
                "Review the school structure currently available to your account.",
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.height(Spacing.md)
        )

        when {
            loading -> {
                SectionLoading(
                    "Loading academic structure…"
                )
            }

            error != null -> {
                SectionError(
                    message = error,
                    onRetry = onRetry
                )
            }

            sections.isEmpty() -> {
                InfoPanel(
                    title =
                        "No sections configured",
                    message =
                        "Your school administrator has not configured academic sections yet."
                )
            }

            else -> {
                val grouped =
                    sections
                        .groupBy {
                            it.gradeLevel
                                .ifBlank {
                                    "Unspecified"
                                }
                        }
                        .toSortedMap(
                            compareBy {
                                gradeSortKey(it)
                            }
                        )

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        )
                ) {
                    grouped.forEach {
                            (
                                grade,
                                gradeSections
                            ) ->
                        GradeAccordion(
                            grade = grade,
                            sections =
                                gradeSections,
                            expanded =
                                expandedGrades[
                                    grade
                                ] == true,
                            onToggle = {
                                expandedGrades[
                                    grade
                                ] =
                                    !(
                                        expandedGrades[
                                            grade
                                        ] == true
                                    )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeAccordion(
    grade: String,
    sections: List<GradeSection>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick =
                            onToggle
                    )
                    .padding(10.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(34.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text =
                                gradeBadge(
                                    grade
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.ExtraBold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimary
                        )
                    }
                }

                Spacer(
                    Modifier.width(9.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            if (
                                grade.startsWith(
                                    "Grade",
                                    ignoreCase =
                                        true
                                )
                            ) {
                                grade
                            } else {
                                "Grade $grade"
                            },
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Text(
                        text =
                            "${sections.size} section${
                                if (
                                    sections.size ==
                                    1
                                ) {
                                    ""
                                } else {
                                    "s"
                                }
                            } · ${
                                sections.count {
                                    it.active
                                }
                            } active",
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                Icon(
                    imageVector =
                        Icons
                            .Filled
                            .KeyboardArrowDown,
                    contentDescription =
                        if (expanded) {
                            "Collapse grade"
                        } else {
                            "Expand grade"
                        },
                    modifier =
                        Modifier.graphicsLayer {
                            rotationZ =
                                if (
                                    expanded
                                ) {
                                    180f
                                } else {
                                    0f
                                }
                        },
                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary
                )
            }

            if (expanded) {
                HorizontalDivider(
                    color =
                        MaterialTheme
                            .colorScheme
                            .outlineVariant
                )

                Column(
                    modifier =
                        Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 8.dp
                        )
                ) {
                    sections
                        .sortedBy {
                            it.sectionName
                                .lowercase()
                        }
                        .forEach {
                                section ->
                            SectionRow(
                                section
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: GradeSection
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                text =
                    section
                        .sectionName
                        .ifBlank {
                            "Section"
                        },
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    section
                        .academicYearName
                        .ifBlank {
                            "Academic year not set"
                        },
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        StatusPill(
            status =
                OperationStatus(
                    if (
                        section.active
                    ) {
                        "Active"
                    } else {
                        "Archived"
                    },
                    if (
                        section.active
                    ) {
                        StatusTone.Success
                    } else {
                        StatusTone.Neutral
                    }
                )
        )
    }
}

@Composable
private fun EntitlementsSection(
    loading: Boolean,
    entitlements:
        TenantEntitlementsResponse?,
    error: String?,
    onRetry: () -> Unit
) {
    val status =
        when {
            loading ->
                OperationStatus(
                    "Loading…",
                    StatusTone.Neutral
                )

            error != null ->
                OperationStatus(
                    "Unavailable",
                    StatusTone.Danger
                )

            entitlements != null ->
                OperationStatus(
                    titleCase(
                        entitlements
                            .subscriptionStatus
                    ),
                    subscriptionTone(
                        entitlements
                            .subscriptionStatus
                    )
                )

            else ->
                OperationStatus(
                    "Unavailable",
                    StatusTone.Neutral
                )
        }

    OperationsCard(
        kicker = "SCHOOL PLAN",
        title =
            "Feature availability",
        icon =
            Icons.Filled.Settings,
        status = status
    ) {
        Text(
            text =
                "Feature availability is controlled by your school's current plan and subscription state.",
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.height(Spacing.md)
        )

        when {
            loading -> {
                SectionLoading(
                    "Loading school features…"
                )
            }

            error != null -> {
                SectionError(
                    message = error,
                    onRetry = onRetry
                )
            }

            entitlements != null -> {
                PlanSummary(
                    entitlements
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                val features =
                    entitlements.features
                        .toSortedMap(
                            compareBy {
                                titleCase(it)
                            }
                        )

                if (
                    features.isEmpty()
                ) {
                    InfoPanel(
                        title =
                            "Standard school features",
                        message =
                            "No individual feature flags were returned for this plan."
                    )
                } else {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            )
                    ) {
                        features.forEach {
                                (
                                    key,
                                    enabled
                                ) ->
                            FeatureRow(
                                label =
                                    titleCase(
                                        key
                                    ),
                                enabled =
                                    enabled
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSummary(
    entitlements:
        TenantEntitlementsResponse
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {
        PlanMetric(
            label = "Plan",
            value =
                titleCase(
                    entitlements.plan
                ),
            modifier =
                Modifier.weight(1f)
        )

        PlanMetric(
            label = "Subscription",
            value =
                titleCase(
                    entitlements
                        .subscriptionStatus
                ),
            modifier =
                Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlanMetric(
    label: String,
    value: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
    ) {
        Column(
            modifier =
                Modifier.padding(10.dp)
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                Modifier.height(2.dp)
            )

            Text(
                text = value,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                fontWeight =
                    FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun FeatureRow(
    label: String,
    enabled: Boolean
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .surface
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier =
                    Modifier.size(31.dp),
                shape = CircleShape,
                color =
                    if (enabled) {
                        MaterialTheme
                            .colorScheme
                            .secondaryContainer
                    } else {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    }
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            if (enabled) {
                                Icons
                                    .Filled
                                    .CheckCircle
                            } else {
                                Icons
                                    .Filled
                                    .RemoveCircleOutline
                            },
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                17.dp
                            ),
                        tint =
                            if (enabled) {
                                MaterialTheme
                                    .colorScheme
                                    .secondary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                            }
                    )
                }
            }

            Spacer(
                Modifier.width(9.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        if (enabled) {
                            "Available for this school"
                        } else {
                            "Not available on the current plan"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OperationsCard(
    kicker: String,
    title: String,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    status: OperationStatus,
    content:
        @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .extraLarge,
        colors =
            CardDefaults
                .elevatedCardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surface
                ),
        elevation =
            CardDefaults
                .elevatedCardElevation(
                    defaultElevation =
                        1.dp
                )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    Spacing.md
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(39.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    19.dp
                                ),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer
                        )
                    }
                }

                Spacer(
                    Modifier.width(9.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text = kicker,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        fontWeight =
                            FontWeight.ExtraBold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Text(
                        text = title,
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )
                }

                Spacer(
                    Modifier.width(7.dp)
                )

                StatusPill(
                    status = status
                )
            }

            Spacer(
                Modifier.height(Spacing.md)
            )

            content()
        }
    }
}

private data class OperationStatus(
    val label: String,
    val tone: StatusTone
)

private enum class StatusTone {
    Neutral,
    Success,
    Info,
    Warning,
    Danger
}

@Composable
private fun StatusPill(
    status: OperationStatus
) {
    val colors =
        when (status.tone) {
            StatusTone.Neutral ->
                MaterialTheme
                    .colorScheme
                    .surfaceVariant to
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant

            StatusTone.Success ->
                MaterialTheme
                    .colorScheme
                    .secondaryContainer to
                    MaterialTheme
                        .colorScheme
                        .secondary

            StatusTone.Info ->
                MaterialTheme
                    .colorScheme
                    .primaryContainer to
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer

            StatusTone.Warning ->
                MaterialTheme
                    .colorScheme
                    .tertiaryContainer to
                    MaterialTheme
                        .colorScheme
                        .onTertiaryContainer

            StatusTone.Danger ->
                MaterialTheme
                    .colorScheme
                    .errorContainer to
                    MaterialTheme
                        .colorScheme
                        .onErrorContainer
        }

    Surface(
        shape = CircleShape,
        color = colors.first
    ) {
        Text(
            text = status.label,
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 5.dp
                ),
            maxLines = 1,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            fontWeight =
                FontWeight.ExtraBold,
            color = colors.second
        )
    }
}

@Composable
private fun SectionLoading(
    message: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 11.dp
            ),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier.size(18.dp),
            strokeWidth = 2.dp
        )

        Spacer(
            Modifier.width(8.dp)
        )

        Text(
            text = message,
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun SectionError(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .errorContainer
    ) {
        Row(
            modifier =
                Modifier.padding(10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                imageVector =
                    Icons
                        .Filled
                        .ErrorOutline,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(19.dp),
                tint =
                    MaterialTheme
                        .colorScheme
                        .onErrorContainer
            )

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                text = message,
                modifier =
                    Modifier.weight(1f),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onErrorContainer
            )

            TextButton(
                onClick = onRetry
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun InfoPanel(
    title: String,
    message: String
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme
                .shapes
                .medium,
        color =
            MaterialTheme
                .colorScheme
                .primaryContainer
    ) {
        Column(
            modifier =
                Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                fontWeight =
                    FontWeight.ExtraBold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer
            )

            Spacer(
                Modifier.height(2.dp)
            )

            Text(
                text = message,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer
            )
        }
    }
}

private fun gradeBadge(
    grade: String
): String =
    grade
        .replace(
            Regex(
                "^grade\\s*",
                RegexOption
                    .IGNORE_CASE
            ),
            ""
        )
        .trim()
        .take(3)
        .ifBlank {
            "G"
        }

private fun gradeSortKey(
    grade: String
): String {
    val numeric =
        Regex("\\d+")
            .find(grade)
            ?.value
            ?.toIntOrNull()

    return if (
        numeric != null
    ) {
        "%05d".format(
            numeric
        )
    } else {
        "99999-${grade.lowercase()}"
    }
}

private fun titleCase(
    value: String
): String =
    value
        .replace(
            "_",
            " "
        )
        .replace(
            "-",
            " "
        )
        .trim()
        .split(
            Regex("\\s+")
        )
        .filter {
            it.isNotBlank()
        }
        .joinToString(" ") {
            part ->
            part.lowercase()
                .replaceFirstChar {
                    char ->
                    char.titlecase()
                }
        }
        .ifBlank {
            "Not set"
        }

private fun subscriptionTone(
    status: String
): StatusTone {
    val normalized =
        status.lowercase()

    return when {
        "active" in normalized ||
            "trial" in normalized ->
            StatusTone.Success

        "past" in normalized ||
            "due" in normalized ||
            "grace" in normalized ->
            StatusTone.Warning

        "cancel" in normalized ||
            "suspend" in normalized ||
            "expired" in normalized ->
            StatusTone.Danger

        else ->
            StatusTone.Neutral
    }
}
