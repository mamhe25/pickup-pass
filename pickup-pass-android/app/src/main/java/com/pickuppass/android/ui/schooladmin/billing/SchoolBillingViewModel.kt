package com.pickuppass.android.ui.schooladmin.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.model.SchoolBillingInvoiceItem
import com.pickuppass.android.data.model.SchoolBillingPaymentInstructions
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SchoolBillingUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val instructions: SchoolBillingPaymentInstructions = SchoolBillingPaymentInstructions(),
    val invoices: List<SchoolBillingInvoiceItem> = emptyList(),
    val paymentNotices: List<GcashPaymentNoticeItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val pdfDocument: BillingPdfPayload? = null
)

data class BillingPdfPayload(val fileName: String, val bytes: ByteArray)


@HiltViewModel
class SchoolBillingViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SchoolBillingUiState())
    val uiState: StateFlow<SchoolBillingUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        when (val r = repository.getBillingCenter()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                loading = false,
                instructions = r.data.paymentInstructions,
                invoices = r.data.invoices,
                paymentNotices = r.data.paymentNotices,
                error = null
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = r.message)
        }
    }

    fun submitPayment(invoiceId: String, payerName: String, reference: String, paidAt: String?, note: String) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = repository.submitGcashPaymentNotice(
                invoiceId = invoiceId,
                payerName = payerName.trim(),
                referenceNumber = reference.trim(),
                paidAt = paidAt?.trim()?.ifBlank { Instant.now().toString() },
                note = note.trim()
            )) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, message = "Payment submitted for manual verification")
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun downloadInvoice(invoice: SchoolBillingInvoiceItem) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null)
            when (val r = repository.downloadBillingInvoicePdf(invoice.invoiceId)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    pdfDocument = BillingPdfPayload(
                        fileName = "${invoice.invoiceNumber.ifBlank { "PickupPass-Invoice" }}.pdf",
                        bytes = r.data
                    )
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun downloadReceipt(invoice: SchoolBillingInvoiceItem) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null)
            when (val r = repository.downloadBillingReceiptPdf(invoice.invoiceId)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    pdfDocument = BillingPdfPayload(
                        fileName = "${invoice.receiptNumber.ifBlank { "PickupPass-Receipt-${invoice.invoiceNumber}" }}.pdf",
                        bytes = r.data
                    )
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun clearPdf() { _uiState.value = _uiState.value.copy(pdfDocument = null) }

}
