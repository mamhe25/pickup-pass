package com.pickuppass.android.ui.schooladmin.billing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.SchoolBillingInvoiceItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolBillingScreen(
    onBack: () -> Unit,
    viewModel: SchoolBillingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var paying by remember { mutableStateOf<SchoolBillingInvoiceItem?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val payload = state.pdfDocument
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.bytes) }
            }
        }
        viewModel.clearPdf()
    }

    LaunchedEffect(state.pdfDocument?.fileName) {
        state.pdfDocument?.let { pdfLauncher.launch(it.fileName) }
    }

    val paidCount = state.invoices.count { it.status == "paid" }
    val openCount = state.invoices.count { it.status !in setOf("paid", "void") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Subscription & Billing", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Invoices & payment verification",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading && state.invoices.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 860.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                state.error?.let { item { ErrorBanner(it) } }
                state.message?.let { item { SuccessBanner(it) } }

                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Icon(
                                    Icons.Filled.AccountBalanceWallet,
                                    null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text("GCash payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (state.instructions.enabled) {
                                    Text(
                                        state.instructions.accountName.ifBlank { "Configured GCash account" },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        state.instructions.mobile,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (state.instructions.note.isNotBlank()) {
                                        Text(state.instructions.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        "Submitting a reference starts manual verification. It does not mark an invoice paid until PickupPass confirms the transaction.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "GCash payment instructions are not configured yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        BillingMetric("Invoices", state.invoices.size, Modifier.weight(1f))
                        BillingMetric("Open", openCount, Modifier.weight(1f))
                        BillingMetric("Paid", paidCount, Modifier.weight(1f))
                    }
                }

                item {
                    Text("Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (!state.loading && state.invoices.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.sm))
                                Text("No invoices yet", fontWeight = FontWeight.Bold)
                                Text(
                                    "Subscription invoices will appear here when issued.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(state.invoices, key = { it.invoiceId }) { invoice ->
                    val pending = state.paymentNotices.firstOrNull {
                        it.invoiceId == invoice.invoiceId && it.status == "pending_review"
                    }
                    InvoiceCard(
                        invoice = invoice,
                        pendingReference = pending?.referenceNumber,
                        instructionsEnabled = state.instructions.enabled,
                        busy = state.saving,
                        onInvoice = { viewModel.downloadInvoice(invoice) },
                        onReceipt = { viewModel.downloadReceipt(invoice) },
                        onPay = { paying = invoice }
                    )
                }

                val rejected = state.paymentNotices.filter { it.status == "rejected" }
                if (rejected.isNotEmpty()) {
                    item {
                        Text("Rejected payment notices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(rejected, key = { it.noticeId }) { notice ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text("${notice.invoiceNumber} · Payment not verified", fontWeight = FontWeight.Bold)
                                Text("Reference: ${notice.referenceNumber}", style = MaterialTheme.typography.bodySmall)
                                if (notice.reviewNote.isNotBlank()) {
                                    Text(
                                        "Review note: ${notice.reviewNote}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    paying?.let { invoice ->
        var payerName by remember(invoice.invoiceId) { mutableStateOf("") }
        var reference by remember(invoice.invoiceId) { mutableStateOf("") }
        var paidAt by remember(invoice.invoiceId) { mutableStateOf(Instant.now().toString()) }
        var note by remember(invoice.invoiceId) { mutableStateOf("") }
        val valid = payerName.isNotBlank() && reference.trim().length >= 4

        AlertDialog(
            onDismissRequest = { if (!state.saving) paying = null },
            icon = { Icon(Icons.Filled.AccountBalanceWallet, null) },
            title = { Text("Submit GCash payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
                            Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold)
                            Text("${invoice.currency} ${moneyLabel(invoice.amountMinor)}")
                        }
                    }
                    Text(
                        "Enter details exactly as shown on the GCash receipt. PickupPass will verify the actual transaction before the invoice is marked paid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        payerName,
                        { payerName = it },
                        label = { Text("GCash account / payer name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        reference,
                        { reference = it },
                        label = { Text("GCash reference number") },
                        singleLine = true,
                        isError = reference.isNotBlank() && reference.trim().length < 4,
                        supportingText = {
                            if (reference.isNotBlank() && reference.trim().length < 4) {
                                Text("Enter the complete payment reference.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        paidAt,
                        { paidAt = it },
                        label = { Text("Payment time (ISO-8601)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = valid && !state.saving,
                    onClick = {
                        viewModel.submitPayment(invoice.invoiceId, payerName, reference, paidAt, note)
                        paying = null
                    }
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Submit for verification")
                }
            },
            dismissButton = {
                TextButton(onClick = { paying = null }, enabled = !state.saving) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InvoiceCard(
    invoice: SchoolBillingInvoiceItem,
    pendingReference: String?,
    instructionsEnabled: Boolean,
    busy: Boolean,
    onInvoice: () -> Unit,
    onReceipt: () -> Unit,
    onPay: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold)
                    Text(
                        "${invoice.currency} ${moneyLabel(invoice.amountMinor)} · Due ${invoice.dueAt?.take(10) ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(invoice.status.pretty()) }
                )
            }

            if (pendingReference != null) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
                        Text("Payment submitted · awaiting verification", fontWeight = FontWeight.SemiBold)
                        Text("Reference: $pendingReference", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (invoice.status == "paid") {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Payment confirmed${invoice.paymentReference.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
            ) {
                OutlinedButton(onClick = onInvoice, enabled = !busy) {
                    Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Invoice PDF")
                }
                if (invoice.receiptAvailable) {
                    OutlinedButton(onClick = onReceipt, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Receipt")
                    }
                }
                if (
                    pendingReference == null &&
                    invoice.status !in setOf("paid", "void") &&
                    instructionsEnabled
                ) {
                    Button(onClick = onPay, enabled = !busy) { Text("I paid") }
                }
            }
        }
    }
}

@Composable
private fun BillingMetric(label: String, value: Int, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun moneyLabel(amountMinor: Long): String = "%.2f".format(amountMinor / 100.0)

private fun String.pretty(): String =
    replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
