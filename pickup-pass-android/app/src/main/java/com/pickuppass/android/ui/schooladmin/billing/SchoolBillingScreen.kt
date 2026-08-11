package com.pickuppass.android.ui.schooladmin.billing

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.SchoolBillingInvoiceItem
import com.pickuppass.android.ui.common.ErrorBanner
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

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val payload = state.pdfDocument
        if (uri != null && payload != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(payload.bytes) } }
        }
        viewModel.clearPdf()
    }
    LaunchedEffect(state.pdfDocument?.fileName) {
        state.pdfDocument?.let { pdfLauncher.launch(it.fileName) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription & Billing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { ErrorBanner(it) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("GCash payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (state.instructions.enabled) {
                            Text(state.instructions.accountName.ifBlank { "Configured GCash account" })
                            Text(state.instructions.mobile, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(state.instructions.note, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Payment is verified manually. Submitting a reference does not mark an invoice paid until PickupPass confirms the actual transaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("GCash payment instructions are not configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Text("Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (!state.loading && state.invoices.isEmpty()) item { Text("No invoices yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.invoices, key = { it.invoiceId }) { invoice ->
                val pending = state.paymentNotices.firstOrNull { it.invoiceId == invoice.invoiceId && it.status == "pending_review" }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(invoice.invoiceNumber, fontWeight = FontWeight.SemiBold)
                            Text(invoice.status.replace('_', ' ').replaceFirstChar { it.uppercase() })
                        }
                        Text("${invoice.currency} ${moneyLabel(invoice.amountMinor)}")
                        Text("Due ${invoice.dueAt?.take(10) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = { viewModel.downloadInvoice(invoice) }, enabled = !state.saving) {
                                Text("Invoice PDF")
                            }
                            if (invoice.receiptAvailable) {
                                OutlinedButton(onClick = { viewModel.downloadReceipt(invoice) }, enabled = !state.saving) {
                                    Text("Receipt PDF")
                                }
                            }
                        }
                        if (pending != null) {
                            Text("GCash payment submitted · awaiting verification", color = MaterialTheme.colorScheme.tertiary)
                            Text("Reference: ${pending.referenceNumber}", style = MaterialTheme.typography.bodySmall)
                        } else if (invoice.status != "paid" && invoice.status != "void" && state.instructions.enabled) {
                            Button(onClick = { paying = invoice }, enabled = !state.saving) { Text("I paid via GCash") }
                        }
                        if (invoice.status == "paid") {
                            Text("Payment confirmed ${invoice.paymentReference}".trim(), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (state.paymentNotices.any { it.status == "rejected" }) {
                item { Text("Payment notices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.paymentNotices.filter { it.status == "rejected" }, key = { it.noticeId }) { notice ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("${notice.invoiceNumber} · Rejected", fontWeight = FontWeight.SemiBold)
                            Text("Reference: ${notice.referenceNumber}")
                            if (notice.reviewNote.isNotBlank()) Text("Reason: ${notice.reviewNote}", color = MaterialTheme.colorScheme.error)
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
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("Submit GCash payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("${invoice.invoiceNumber} · ${invoice.currency} ${moneyLabel(invoice.amountMinor)}", fontWeight = FontWeight.SemiBold)
                    Text("Verify the amount and reference against your GCash receipt before submitting.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(payerName, { payerName = it }, label = { Text("GCash account / payer name") }, singleLine = true)
                    OutlinedTextField(reference, { reference = it }, label = { Text("GCash reference number") }, singleLine = true)
                    OutlinedTextField(paidAt, { paidAt = it }, label = { Text("Payment time (ISO-8601)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
                }
            },
            confirmButton = {
                Button(
                    enabled = payerName.isNotBlank() && reference.length >= 4 && !state.saving,
                    onClick = {
                        viewModel.submitPayment(invoice.invoiceId, payerName, reference, paidAt, note)
                        paying = null
                    }
                ) { Text("Submit for verification") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } }
        )
    }
}

private fun moneyLabel(amountMinor: Long): String = "%.2f".format(amountMinor / 100.0)
