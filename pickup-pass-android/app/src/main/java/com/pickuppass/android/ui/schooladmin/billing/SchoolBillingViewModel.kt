package com.pickuppass.android.ui.schooladmin.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.model.SchoolBillingInvoiceItem
import com.pickuppass.android.data.model.SchoolBillingPaymentInstructions
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import com.pickuppass.android.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SchoolBillingUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val actionLabel: String = "",
    val entitlements: TenantEntitlementsResponse =
        TenantEntitlementsResponse(),
    val instructions: SchoolBillingPaymentInstructions =
        SchoolBillingPaymentInstructions(),
    val invoices: List<SchoolBillingInvoiceItem> = emptyList(),
    val paymentNotices: List<GcashPaymentNoticeItem> = emptyList(),
    val error: String? = null,
    val errorTitle: String? = null,
    val message: String? = null,
    val messageTitle: String? = null,
    val pdfDocument: BillingPdfPayload? = null
)

data class BillingPdfPayload(
    val fileName: String,
    val bytes: ByteArray,
    val kind: BillingDocumentKind
)

enum class BillingDocumentKind {
    Invoice,
    Receipt
}

@HiltViewModel
class SchoolBillingViewModel @Inject constructor(
    private val repository: SchoolAdminRepository,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(SchoolBillingUiState())
    val uiState: StateFlow<SchoolBillingUiState> =
        _uiState

    private var loadInProgress = false

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        if (loadInProgress || _uiState.value.saving) return

        loadInProgress = true
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    loading = initial,
                    refreshing = !initial,
                    error = null,
                    errorTitle = null
                )

            val billingResult =
                repository.getBillingCenter()
            val entitlementResult =
                schoolRepository.getEntitlements()

            val current = _uiState.value

            when (billingResult) {
                is ApiResult.Success -> {
                    val entitlements =
                        when (entitlementResult) {
                            is ApiResult.Success ->
                                entitlementResult.data

                            is ApiResult.Failure ->
                                current.entitlements
                        }

                    _uiState.value =
                        current.copy(
                            loading = false,
                            refreshing = false,
                            instructions =
                                billingResult.data
                                    .paymentInstructions,
                            invoices =
                                billingResult.data.invoices,
                            paymentNotices =
                                billingResult.data
                                    .paymentNotices,
                            entitlements = entitlements,
                            error =
                                if (
                                    entitlementResult
                                        is ApiResult.Failure
                                ) {
                                    entitlementResult.message
                                } else {
                                    null
                                },
                            errorTitle =
                                if (
                                    entitlementResult
                                        is ApiResult.Failure
                                ) {
                                    "Plan details not refreshed"
                                } else {
                                    null
                                }
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        current.copy(
                            loading = false,
                            refreshing = false,
                            errorTitle =
                                if (initial) {
                                    "Billing center unavailable"
                                } else {
                                    "Billing center not refreshed"
                                },
                            error =
                                billingResult.message
                        )
                }
            }

            loadInProgress = false
        }
    }

    fun submitPayment(
        invoice: SchoolBillingInvoiceItem,
        payerName: String,
        reference: String,
        paidAt: String?,
        note: String
    ) {
        if (_uiState.value.saving || loadInProgress) return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    saving = true,
                    actionLabel = "Submitting payment notice…",
                    error = null,
                    errorTitle = null,
                    message = null,
                    messageTitle = null
                )

            when (
                val result =
                    repository.submitGcashPaymentNotice(
                        invoiceId = invoice.invoiceId,
                        payerName = payerName.trim(),
                        referenceNumber =
                            reference.trim(),
                        paidAt =
                            paidAt
                                ?.trim()
                                ?.ifBlank {
                                    Instant.now()
                                        .toString()
                                },
                        note = note.trim()
                    )
            ) {
                is ApiResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            actionLabel = "",
                            messageTitle =
                                "Payment submitted",
                            message =
                                "Reference " +
                                    result.data
                                        .referenceNumber +
                                    " was submitted for manual verification. " +
                                    invoice.invoiceNumber +
                                    " remains unpaid until PickupPass confirms the transaction."
                        )

                    reloadAfterMutation()
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            actionLabel = "",
                            errorTitle =
                                "Payment notice not submitted",
                            error = result.message
                        )
                }
            }
        }
    }

    fun downloadInvoice(
        invoice: SchoolBillingInvoiceItem
    ) {
        downloadDocument(
            invoice = invoice,
            kind = BillingDocumentKind.Invoice
        )
    }

    fun downloadReceipt(
        invoice: SchoolBillingInvoiceItem
    ) {
        downloadDocument(
            invoice = invoice,
            kind = BillingDocumentKind.Receipt
        )
    }

    private fun downloadDocument(
        invoice: SchoolBillingInvoiceItem,
        kind: BillingDocumentKind
    ) {
        if (_uiState.value.saving || loadInProgress) return

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    saving = true,
                    actionLabel =
                        if (
                            kind ==
                            BillingDocumentKind.Invoice
                        ) {
                            "Preparing invoice PDF…"
                        } else {
                            "Preparing receipt PDF…"
                        },
                    error = null,
                    errorTitle = null
                )

            val result =
                if (
                    kind ==
                    BillingDocumentKind.Invoice
                ) {
                    repository
                        .downloadBillingInvoicePdf(
                            invoice.invoiceId
                        )
                } else {
                    repository
                        .downloadBillingReceiptPdf(
                            invoice.invoiceId
                        )
                }

            when (result) {
                is ApiResult.Success -> {
                    val fileName =
                        if (
                            kind ==
                            BillingDocumentKind.Invoice
                        ) {
                            (
                                invoice.invoiceNumber
                                    .ifBlank {
                                        "PickupPass-Invoice"
                                    }
                                ) +
                                ".pdf"
                        } else {
                            (
                                invoice.receiptNumber
                                    .ifBlank {
                                        "PickupPass-Receipt-" +
                                            invoice.invoiceNumber
                                    }
                                ) +
                                ".pdf"
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            actionLabel = "",
                            pdfDocument =
                                BillingPdfPayload(
                                    fileName =
                                        fileName,
                                    bytes = result.data,
                                    kind = kind
                                )
                        )
                }

                is ApiResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            saving = false,
                            actionLabel = "",
                            errorTitle =
                                if (
                                    kind ==
                                    BillingDocumentKind.Invoice
                                ) {
                                    "Invoice not downloaded"
                                } else {
                                    "Receipt not downloaded"
                                },
                            error = result.message
                        )
                }
            }
        }
    }

    fun documentSaved(
        kind: BillingDocumentKind
    ) {
        _uiState.value =
            _uiState.value.copy(
                pdfDocument = null,
                messageTitle =
                    if (
                        kind ==
                        BillingDocumentKind.Invoice
                    ) {
                        "Invoice saved"
                    } else {
                        "Receipt saved"
                    },
                message =
                    if (
                        kind ==
                        BillingDocumentKind.Invoice
                    ) {
                        "The invoice PDF was saved successfully."
                    } else {
                        "The payment receipt PDF was saved successfully."
                    }
            )
    }

    fun documentSaveFailed(
        kind: BillingDocumentKind,
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                pdfDocument = null,
                errorTitle =
                    if (
                        kind ==
                        BillingDocumentKind.Invoice
                    ) {
                        "Invoice not saved"
                    } else {
                        "Receipt not saved"
                    },
                error = message
            )
    }

    fun clearPdf() {
        _uiState.value =
            _uiState.value.copy(
                pdfDocument = null
            )
    }

    fun clearFeedback() {
        _uiState.value =
            _uiState.value.copy(
                error = null,
                errorTitle = null,
                message = null,
                messageTitle = null
            )
    }

    private suspend fun reloadAfterMutation() {
        when (val result = repository.getBillingCenter()) {
            is ApiResult.Success -> {
                _uiState.value =
                    _uiState.value.copy(
                        invoices =
                            result.data.invoices,
                        paymentNotices =
                            result.data.paymentNotices,
                        instructions =
                            result.data.paymentInstructions
                    )
            }

            is ApiResult.Failure -> {
                _uiState.value =
                    _uiState.value.copy(
                        errorTitle =
                            "Payment submitted, refresh failed",
                        error =
                            "The payment notice was submitted, but the latest billing data could not be loaded. " +
                                result.message
                    )
            }
        }
    }
}
