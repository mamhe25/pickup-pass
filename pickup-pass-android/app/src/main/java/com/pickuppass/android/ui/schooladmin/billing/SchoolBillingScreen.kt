package com.pickuppass.android.ui.schooladmin.billing

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.model.SchoolBillingInvoiceItem
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SchoolBillingScreen(
    onBack: () -> Unit,
    viewModel: SchoolBillingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var paying by remember {
        mutableStateOf<SchoolBillingInvoiceItem?>(null)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/pdf"
        )
    ) { uri ->
        val payload = state.pdfDocument

        if (payload == null) {
            return@rememberLauncherForActivityResult
        }

        if (uri == null) {
            viewModel.clearPdf()
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver
                .openOutputStream(uri)
                ?.use {
                    it.write(payload.bytes)
                }
                ?: error(
                    "Could not open the selected save location"
                )
        }.onSuccess {
            viewModel.documentSaved(payload.kind)
        }.onFailure {
            viewModel.documentSaveFailed(
                payload.kind,
                it.message
                    ?: "Could not save the billing document"
            )
        }
    }

    LaunchedEffect(
        state.pdfDocument?.fileName
    ) {
        state.pdfDocument?.let {
            pdfLauncher.launch(it.fileName)
        }
    }

    val paidCount =
        state.invoices.count {
            it.status.equals(
                "paid",
                ignoreCase = true
            )
        }

    val openCount =
        state.invoices.count {
            it.status.lowercase() !in
                setOf("paid", "void")
        }

    val overdueCount =
        state.invoices.count {
            invoiceIsOverdue(it)
        }

    val pendingNotices =
        remember(state.paymentNotices) {
            state.paymentNotices.filter {
                it.status.equals(
                    "pending_review",
                    ignoreCase = true
                )
            }
        }

    Scaffold(
        containerColor =
            MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Subscription & billing",
                subtitle =
                    "Plan access, invoices & verification",
                onBack = onBack,
            )
        }
    ) { padding ->
        if (
            state.loading &&
            state.invoices.isEmpty()
        ) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            enabled = !state.saving,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 860.dp)
                        .align(Alignment.TopCenter)
                        .imePadding(),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    item(key = "hero") {
                        PremiumHeroCard(
                            eyebrow =
                                "School subscription",
                            title =
                                subscriptionHeroTitle(
                                    state.entitlements
                                ),
                            message =
                                subscriptionHeroMessage(
                                    state.entitlements
                                ),
                            icon =
                                Icons.Filled.VerifiedUser
                        )
                    }

                    item(key = "subscription") {
                        SubscriptionOverviewCard(
                            entitlements =
                                state.entitlements
                        )
                    }

                    item(key = "billing-metrics") {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    Spacing.sm
                                )
                        ) {
                            BillingMetric(
                                label = "Open",
                                value = openCount,
                                modifier =
                                    Modifier.weight(1f)
                            )
                            BillingMetric(
                                label = "Pending",
                                value =
                                    pendingNotices.size,
                                modifier =
                                    Modifier.weight(1f)
                            )
                            BillingMetric(
                                label =
                                    if (
                                        overdueCount > 0
                                    ) {
                                        "Overdue"
                                    } else {
                                        "Paid"
                                    },
                                value =
                                    if (
                                        overdueCount > 0
                                    ) {
                                        overdueCount
                                    } else {
                                        paidCount
                                    },
                                modifier =
                                    Modifier.weight(1f),
                                attention =
                                    overdueCount > 0
                            )
                        }
                    }

                    if (
                        state.saving &&
                        state.actionLabel.isNotBlank()
                    ) {
                        item(key = "action-progress") {
                            BillingActionProgress(
                                state.actionLabel
                            )
                        }
                    }

                    item(key = "payment-method") {
                        PaymentInstructionsCard(
                            enabled =
                                state.instructions.enabled,
                            accountName =
                                state.instructions.accountName,
                            mobile =
                                state.instructions.mobile,
                            note =
                                state.instructions.note
                        )
                    }

                    item(key = "invoice-header") {
                        PremiumSectionHeader(
                            title = "Invoices",
                            subtitle =
                                state.invoices.size
                                    .toString() +
                                    " billing invoice" +
                                    if (
                                        state.invoices.size ==
                                        1
                                    ) {
                                        ""
                                    } else {
                                        "s"
                                    }
                        )
                    }

                    if (
                        !state.loading &&
                        state.invoices.isEmpty()
                    ) {
                        item(key = "invoice-empty") {
                            BillingEmptyState()
                        }
                    } else {
                        items(
                            items = state.invoices,
                            key = {
                                it.invoiceId
                            }
                        ) { invoice ->
                            val pending =
                                state.paymentNotices
                                    .firstOrNull {
                                        it.invoiceId ==
                                            invoice.invoiceId &&
                                            it.status.equals(
                                                "pending_review",
                                                ignoreCase =
                                                    true
                                            )
                                    }

                            val rejected =
                                state.paymentNotices
                                    .firstOrNull {
                                        it.invoiceId ==
                                            invoice.invoiceId &&
                                            it.status.equals(
                                                "rejected",
                                                ignoreCase =
                                                    true
                                            )
                                    }

                            InvoiceCard(
                                invoice = invoice,
                                pendingNotice = pending,
                                rejectedNotice =
                                    rejected,
                                instructionsEnabled =
                                    state.instructions
                                        .enabled,
                                busy =
                                    state.saving ||
                                        state.refreshing,
                                onInvoice = {
                                    viewModel
                                        .downloadInvoice(
                                            invoice
                                        )
                                },
                                onReceipt = {
                                    viewModel
                                        .downloadReceipt(
                                            invoice
                                        )
                                },
                                onPay = {
                                    paying = invoice
                                }
                            )
                        }
                    }

                    item(key = "verification-note") {
                        Surface(
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                MaterialTheme.shapes.large,
                            color =
                                MaterialTheme.colorScheme
                                    .surfaceContainerLow
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(
                                        Spacing.md
                                    ),
                                verticalAlignment =
                                    Alignment.Top
                            ) {
                                Icon(
                                    Icons.Filled
                                        .AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier =
                                        Modifier.size(18.dp),
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )

                                Spacer(
                                    Modifier.width(
                                        Spacing.sm
                                    )
                                )

                                Text(
                                    text =
                                        "A GCash reference is only a payment notice. Subscription access and invoice status change only after PickupPass verifies the actual transaction. Rejected notices can be corrected and resubmitted with a valid reference.",
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
                    }
                }
            }
        }
    }

    paying?.let { invoice ->
        PaymentSubmissionDialog(
            invoice = invoice,
            saving = state.saving,
            onDismiss = {
                if (!state.saving) {
                    paying = null
                }
            },
            onSubmit = {
                    payerName,
                    reference,
                    paidAt,
                    note ->
                viewModel.submitPayment(
                    invoice = invoice,
                    payerName = payerName,
                    reference = reference,
                    paidAt = paidAt,
                    note = note
                )
                paying = null
            }
        )
    }

    // Keep terminal feedback last so it stays above the payment dialog.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                state.errorTitle
                    ?: "Billing action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.message?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                state.messageTitle
                    ?: "Billing action completed",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun SubscriptionOverviewCard(
    entitlements: TenantEntitlementsResponse
) {
    val scheme = MaterialTheme.colorScheme
    val accessActive =
        entitlements.subscriptionAccessActive

    val enabledFeatures =
        entitlements.features.count {
            it.value
        }

    val totalFeatures =
        entitlements.features.size

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            entitlements.plan
                                .ifBlank {
                                    "trial"
                                }
                                .pretty(),
                        style =
                            MaterialTheme.typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Text(
                        text =
                            entitlements
                                .subscriptionStatus
                                .ifBlank {
                                    "unknown"
                                }
                                .pretty(),
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            scheme.onSurfaceVariant
                    )
                }

                SubscriptionAccessBadge(
                    active = accessActive
                )
            }

            HorizontalDivider()

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm
                    )
            ) {
                entitlements.trialEndsAt
                    ?.let {
                        SubscriptionDetail(
                            label =
                                "Trial ends",
                            value =
                                formatBillingInstant(
                                    it
                                )
                        )
                    }

                entitlements.currentPeriodEnd
                    ?.let {
                        SubscriptionDetail(
                            label =
                                "Current period ends",
                            value =
                                formatBillingInstant(
                                    it
                                )
                        )
                    }

                entitlements.graceEndsAt
                    ?.let {
                        SubscriptionDetail(
                            label =
                                "Grace period ends",
                            value =
                                formatBillingInstant(
                                    it
                                ),
                            attention = true
                        )
                    }

                SubscriptionDetail(
                    label = "Renewal",
                    value =
                        when {
                            entitlements
                                .cancelAtPeriodEnd ->
                                "Ends at current period"

                            entitlements.autoRenew ->
                                "Automatic renewal"

                            else ->
                                "Manual renewal"
                        }
                )

                if (totalFeatures > 0) {
                    SubscriptionDetail(
                        label =
                            "Enabled features",
                        value =
                            enabledFeatures
                                .toString() +
                                " of " +
                                totalFeatures
                    )
                }
            }

            if (
                entitlements.features.isNotEmpty()
            ) {
                HorizontalDivider()

                Text(
                    text = "PLAN FEATURES",
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight =
                        FontWeight.ExtraBold,
                    color = scheme.primary
                )

                FlowRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.xs
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            Spacing.xs
                        )
                ) {
                    entitlements.features
                        .entries
                        .sortedBy {
                            it.key
                        }
                        .take(8)
                        .forEach {
                            FeatureChip(
                                name =
                                    it.key.pretty(),
                                enabled =
                                    it.value
                            )
                        }
                }
            }

            if (
                entitlements.limits.isNotEmpty()
            ) {
                HorizontalDivider()

                Text(
                    text = "PLAN LIMITS",
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight =
                        FontWeight.ExtraBold,
                    color = scheme.primary
                )

                FlowRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.xs
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            Spacing.xs
                        )
                ) {
                    entitlements.limits
                        .entries
                        .sortedBy {
                            it.key
                        }
                        .take(8)
                        .forEach {
                            LimitChip(
                                name =
                                    it.key.pretty(),
                                value =
                                    it.value
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionAccessBadge(
    active: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (active) {
                scheme.primaryContainer
            } else {
                scheme.errorContainer
            }
    ) {
        Text(
            text =
                if (active) {
                    "ACCESS ACTIVE"
                } else {
                    "ACCESS LIMITED"
                },
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            fontWeight =
                FontWeight.ExtraBold,
            color =
                if (active) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onErrorContainer
                }
        )
    }
}

@Composable
private fun SubscriptionDetail(
    label: String,
    value: String,
    attention: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography
                    .bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(Modifier.width(Spacing.sm))

        Text(
            text = value,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold,
            color =
                if (attention) {
                    MaterialTheme
                        .colorScheme.error
                } else {
                    MaterialTheme
                        .colorScheme.onSurface
                }
        )
    }
}

@Composable
private fun FeatureChip(
    name: String,
    enabled: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (enabled) {
                scheme.primaryContainer
                    .copy(alpha = 0.60f)
            } else {
                scheme.surfaceVariant
            }
    ) {
        Text(
            text =
                if (enabled) {
                    "✓ " + name
                } else {
                    name
                },
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            color =
                if (enabled) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onSurfaceVariant
                }
        )
    }
}

@Composable
private fun LimitChip(
    name: String,
    value: Int
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
    ) {
        Text(
            text =
                name +
                    ": " +
                    if (value < 0) {
                        "Unlimited"
                    } else {
                        value.toString()
                    },
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun PaymentInstructionsCard(
    enabled: Boolean,
    accountName: String,
    mobile: String,
    note: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape =
                    MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            ) {
                Icon(
                    Icons.Filled
                        .AccountBalanceWallet,
                    contentDescription = null,
                    modifier =
                        Modifier.padding(12.dp),
                    tint =
                        MaterialTheme.colorScheme
                            .primary
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.xs
                    )
            ) {
                Text(
                    text = "GCash payment",
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (enabled) {
                    Text(
                        text =
                            accountName
                                .ifBlank {
                                    "Configured GCash account"
                                },
                        style =
                            MaterialTheme.typography
                                .bodyMedium
                    )

                    Text(
                        text = mobile,
                        style =
                            MaterialTheme.typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    if (note.isNotBlank()) {
                        Text(
                            text = note,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }

                    Text(
                        text =
                            "Send the exact invoice amount. Keep the reference number from your receipt for verification.",
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                } else {
                    Text(
                        text =
                            "GCash instructions are not configured. Payment submission is unavailable until PickupPass configures the receiving account.",
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: SchoolBillingInvoiceItem,
    pendingNotice: GcashPaymentNoticeItem?,
    rejectedNotice: GcashPaymentNoticeItem?,
    instructionsEnabled: Boolean,
    busy: Boolean,
    onInvoice: () -> Unit,
    onReceipt: () -> Unit,
    onPay: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val overdue = invoiceIsOverdue(invoice)
    val paid =
        invoice.status.equals(
            "paid",
            ignoreCase = true
        )

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            1.dp,
            when {
                overdue ->
                    scheme.error.copy(
                        alpha = 0.30f
                    )

                paid ->
                    scheme.primary.copy(
                        alpha = 0.20f
                    )

                else ->
                    scheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            invoice.invoiceNumber,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            invoice.currency +
                                " " +
                                moneyLabel(
                                    invoice.amountMinor
                                ),
                        style =
                            MaterialTheme.typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    val metadata =
                        buildList {
                            if (
                                invoice.planSnapshot
                                    .isNotBlank()
                            ) {
                                add(
                                    invoice.planSnapshot
                                        .pretty()
                                )
                            }

                            invoice.dueAt
                                ?.let {
                                    add(
                                        "Due " +
                                            formatBillingDate(
                                                it
                                            )
                                    )
                                }
                        }

                    if (metadata.isNotEmpty()) {
                        Text(
                            text =
                                metadata.joinToString(
                                    " · "
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            color =
                                if (overdue) {
                                    scheme.error
                                } else {
                                    scheme
                                        .onSurfaceVariant
                                }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.sm))

                InvoiceStatusBadge(
                    status = invoice.status,
                    overdue = overdue
                )
            }

            if (invoice.note.isNotBlank()) {
                Text(
                    text = invoice.note,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        scheme.onSurfaceVariant
                )
            }

            when {
                pendingNotice != null -> {
                    BillingNoticeCard(
                        title =
                            "Payment awaiting verification",
                        message =
                            "Reference " +
                                pendingNotice
                                    .referenceNumber +
                                " is under manual review.",
                        tone =
                            BillingNoticeTone.Pending
                    )
                }

                paid -> {
                    BillingNoticeCard(
                        title =
                            "Payment confirmed",
                        message =
                            invoice
                                .paymentReference
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    "Verified reference: " +
                                        it
                                }
                                ?: "PickupPass has verified this invoice payment.",
                        tone =
                            BillingNoticeTone.Success
                    )
                }

                rejectedNotice != null -> {
                    BillingNoticeCard(
                        title =
                            "Previous payment notice rejected",
                        message =
                            buildString {
                                append(
                                    "Reference "
                                )
                                append(
                                    rejectedNotice
                                        .referenceNumber
                                )
                                append(
                                    " was not verified."
                                )

                                if (
                                    rejectedNotice
                                        .reviewNote
                                        .isNotBlank()
                                ) {
                                    append(" ")
                                    append(
                                        rejectedNotice
                                            .reviewNote
                                    )
                                }

                                append(
                                    " You may submit a corrected payment reference."
                                )
                            },
                        tone =
                            BillingNoticeTone.Error
                    )
                }

                overdue -> {
                    BillingNoticeCard(
                        title = "Invoice overdue",
                        message =
                            "This invoice is past its due date and remains unpaid.",
                        tone =
                            BillingNoticeTone.Error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm,
                        Alignment.End
                    )
            ) {
                OutlinedButton(
                    onClick = onInvoice,
                    enabled = !busy
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        modifier =
                            Modifier.size(18.dp)
                    )
                    Spacer(
                        Modifier.width(
                            Spacing.xs
                        )
                    )
                    Text("Invoice")
                }

                if (invoice.receiptAvailable) {
                    OutlinedButton(
                        onClick = onReceipt,
                        enabled = !busy
                    ) {
                        Icon(
                            Icons.AutoMirrored
                                .Filled
                                .ReceiptLong,
                            contentDescription = null,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Spacer(
                            Modifier.width(
                                Spacing.xs
                            )
                        )
                        Text("Receipt")
                    }
                }

                if (
                    pendingNotice == null &&
                    !paid &&
                    !invoice.status.equals(
                        "void",
                        ignoreCase = true
                    ) &&
                    instructionsEnabled
                ) {
                    Button(
                        onClick = onPay,
                        enabled = !busy
                    ) {
                        Text(
                            if (
                                rejectedNotice != null
                            ) {
                                "Resubmit"
                            } else {
                                "I paid"
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class BillingNoticeTone {
    Pending,
    Success,
    Error
}

@Composable
private fun BillingNoticeCard(
    title: String,
    message: String,
    tone: BillingNoticeTone
) {
    val scheme = MaterialTheme.colorScheme

    val container =
        when (tone) {
            BillingNoticeTone.Pending ->
                scheme.tertiaryContainer

            BillingNoticeTone.Success ->
                scheme.primaryContainer

            BillingNoticeTone.Error ->
                scheme.errorContainer
        }

    val content =
        when (tone) {
            BillingNoticeTone.Pending ->
                scheme.onTertiaryContainer

            BillingNoticeTone.Success ->
                scheme.onPrimaryContainer

            BillingNoticeTone.Error ->
                scheme.onErrorContainer
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement =
                Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontWeight =
                    FontWeight.SemiBold,
                color = content
            )
            Text(
                text = message,
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color = content
            )
        }
    }
}

@Composable
private fun InvoiceStatusBadge(
    status: String,
    overdue: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val normalized =
        status.lowercase()

    val container =
        when {
            overdue ->
                scheme.errorContainer

            normalized == "paid" ->
                scheme.primaryContainer

            normalized == "void" ->
                scheme.surfaceVariant

            else ->
                scheme.tertiaryContainer
        }

    val content =
        when {
            overdue ->
                scheme.onErrorContainer

            normalized == "paid" ->
                scheme.onPrimaryContainer

            normalized == "void" ->
                scheme.onSurfaceVariant

            else ->
                scheme.onTertiaryContainer
        }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = container
    ) {
        Text(
            text =
                if (overdue) {
                    "OVERDUE"
                } else {
                    status
                        .ifBlank {
                            "open"
                        }
                        .pretty()
                        .uppercase()
                },
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            fontWeight =
                FontWeight.ExtraBold,
            color = content
        )
    }
}

@Composable
private fun PaymentSubmissionDialog(
    invoice: SchoolBillingInvoiceItem,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        String,
        String,
        String,
        String
    ) -> Unit
) {
    val context = LocalContext.current

    var payerName by remember(
        invoice.invoiceId
    ) {
        mutableStateOf("")
    }

    var reference by remember(
        invoice.invoiceId
    ) {
        mutableStateOf("")
    }

    var paidAt by remember(
        invoice.invoiceId
    ) {
        mutableStateOf(
            ZonedDateTime.now()
        )
    }

    var note by remember(
        invoice.invoiceId
    ) {
        mutableStateOf("")
    }

    val valid =
        payerName.isNotBlank() &&
            reference.trim().length >= 4

    fun choosePaymentTime() {
        val initial = paidAt

        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        paidAt =
                            ZonedDateTime.of(
                                year,
                                month + 1,
                                day,
                                hour,
                                minute,
                                0,
                                0,
                                ZoneId
                                    .systemDefault()
                            )
                    },
                    initial.hour,
                    initial.minute,
                    DateFormat
                        .is24HourFormat(
                            context
                        )
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).apply {
            datePicker.maxDate =
                System.currentTimeMillis()
        }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled
                    .AccountBalanceWallet,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = "Submit GCash payment",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm
                    )
            ) {
                Surface(
                    shape =
                        MaterialTheme.shapes.medium,
                    color =
                        MaterialTheme.colorScheme
                            .surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                Spacing.sm
                            )
                    ) {
                        Text(
                            text =
                                invoice.invoiceNumber,
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            text =
                                invoice.currency +
                                    " " +
                                    moneyLabel(
                                        invoice
                                            .amountMinor
                                    ),
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text =
                        "Enter details exactly as shown on the GCash receipt. This submits a verification notice only; the invoice remains unpaid until the transaction is confirmed.",
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                OutlinedTextField(
                    value = payerName,
                    onValueChange = {
                        payerName = it
                            .take(120)
                    },
                    label = {
                        Text(
                            "GCash account / payer name"
                        )
                    },
                    singleLine = true,
                    enabled = !saving,
                    modifier =
                        Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reference,
                    onValueChange = {
                        reference =
                            it.take(120)
                    },
                    label = {
                        Text(
                            "GCash reference number"
                        )
                    },
                    singleLine = true,
                    enabled = !saving,
                    isError =
                        reference.isNotBlank() &&
                            reference
                                .trim()
                                .length < 4,
                    supportingText = {
                        if (
                            reference.isNotBlank() &&
                            reference
                                .trim()
                                .length < 4
                        ) {
                            Text(
                                "Enter the complete payment reference."
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = ::choosePaymentTime,
                    enabled = !saving,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier =
                            Modifier.size(18.dp)
                    )
                    Spacer(
                        Modifier.width(
                            Spacing.xs
                        )
                    )
                    Text(
                        "Paid " +
                            paidAt.format(
                                BILLING_LOCAL_FORMAT
                            )
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it.take(500)
                    },
                    label = {
                        Text("Note (optional)")
                    },
                    minLines = 2,
                    maxLines = 3,
                    enabled = !saving,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !saving,
                onClick = {
                    onSubmit(
                        payerName.trim(),
                        reference.trim(),
                        paidAt
                            .toInstant()
                            .toString(),
                        note.trim()
                    )
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(
                        Modifier.width(
                            Spacing.xs
                        )
                    )
                }
                Text(
                    if (saving) {
                        "Submitting…"
                    } else {
                        "Submit for verification"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !saving
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BillingMetric(
    label: String,
    value: Int,
    modifier: Modifier,
    attention: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.large,
        color =
            if (attention) {
                scheme.errorContainer
            } else {
                scheme.surfaceVariant
            },
        modifier = modifier
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.sm)
        ) {
            Text(
                text = value.toString(),
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight =
                    FontWeight.ExtraBold,
                color =
                    if (attention) {
                        scheme.onErrorContainer
                    } else {
                        scheme.onSurface
                    }
            )
            Text(
                text = label,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                color =
                    if (attention) {
                        scheme.onErrorContainer
                    } else {
                        scheme.onSurfaceVariant
                    }
            )
        }
    }
}

@Composable
private fun BillingActionProgress(
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .secondaryContainer
    ) {
        Row(
            modifier =
                Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(
                Modifier.width(Spacing.sm)
            )
            Text(
                text = message,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BillingEmptyState() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme
                        .primary,
                modifier =
                    Modifier.size(30.dp)
            )

            Text(
                text = "No invoices yet",
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Subscription invoices will appear here when they are issued.",
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

private fun subscriptionHeroTitle(
    entitlements:
        TenantEntitlementsResponse
): String =
    if (
        entitlements.subscriptionAccessActive
    ) {
        entitlements.plan
            .ifBlank {
                "PickupPass"
            }
            .pretty() +
            " access is active"
    } else {
        "Subscription access needs attention"
    }

private fun subscriptionHeroMessage(
    entitlements:
        TenantEntitlementsResponse
): String {
    return when {
        !entitlements
            .subscriptionAccessActive ->
            "Review open invoices and subscription dates below. Some plan-gated features may be unavailable until access is restored."

        entitlements.cancelAtPeriodEnd ->
            "Access is active for the current period but is scheduled to end at the period boundary."

        entitlements
            .subscriptionStatus
            .equals(
                "trialing",
                ignoreCase = true
            ) ->
            "Your school is currently using PickupPass under an active trial. Review the trial end date and available plan features below."

        else ->
            "Your school subscription is active. Review renewal timing, plan features, invoices, and verified payment records below."
    }
}

private fun invoiceIsOverdue(
    invoice: SchoolBillingInvoiceItem
): Boolean {
    if (
        invoice.status.lowercase() in
        setOf("paid", "void")
    ) {
        return false
    }

    val due =
        invoice.dueAt
            ?.let {
                runCatching {
                    Instant.parse(it)
                }.getOrNull()
            }
            ?: return false

    return due.isBefore(Instant.now())
}

private fun formatBillingDate(
    value: String
): String =
    runCatching {
        Instant.parse(value)
            .atZone(
                ZoneId.systemDefault()
            )
            .format(
                BILLING_DATE_FORMAT
            )
    }.getOrElse {
        value.take(10)
    }

private fun formatBillingInstant(
    value: String
): String =
    runCatching {
        Instant.parse(value)
            .atZone(
                ZoneId.systemDefault()
            )
            .format(
                BILLING_LOCAL_FORMAT
            )
    }.getOrElse {
        value
    }

private fun moneyLabel(
    amountMinor: Long
): String =
    "%,.2f".format(
        amountMinor / 100.0
    )

private fun String.pretty(): String =
    replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(
            Regex("\\s+")
        )
        .joinToString(" ") {
            it.replaceFirstChar { c ->
                if (c.isLowerCase()) {
                    c.titlecase()
                } else {
                    c.toString()
                }
            }
        }

private val BILLING_DATE_FORMAT:
    DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "MMM d, yyyy"
    )

private val BILLING_LOCAL_FORMAT:
    DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "MMM d, yyyy · h:mm a"
    )
