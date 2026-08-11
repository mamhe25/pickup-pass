package com.pickuppass.android.ui.masteradmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.MasterPlanDefinition
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.data.model.MasterInvoiceItem
import com.pickuppass.android.data.model.MasterBillingProfileResponse
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.MasterAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoicePdfPayload(val fileName: String, val bytes: ByteArray)

data class MasterAdminUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val totalSchools: Int = 0,
    val activeSchools: Int = 0,
    val suspendedSchools: Int = 0,
    val schools: List<MasterSchoolItem> = emptyList(),
    val plans: Map<String, MasterPlanDefinition> = emptyMap(),
    val featureKeys: List<String> = emptyList(),
    val billingSchoolId: String? = null,
    val billingLoading: Boolean = false,
    val invoices: List<MasterInvoiceItem> = emptyList(),
    val billingProfile: MasterBillingProfileResponse? = null,
    val gcashPaymentNotices: List<GcashPaymentNoticeItem> = emptyList(),
    val invoicePdf: InvoicePdfPayload? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class MasterAdminViewModel @Inject constructor(
    private val repository: MasterAdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MasterAdminUiState())
    val uiState: StateFlow<MasterAdminUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        val plansResult = repository.getPlanCatalog()
        val schoolsResult = repository.listSchools()
        when (schoolsResult) {
            is ApiResult.Success -> {
                val planData = when (plansResult) {
                    is ApiResult.Success -> plansResult.data
                    is ApiResult.Failure -> null
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    totalSchools = schoolsResult.data.totalSchools,
                    activeSchools = schoolsResult.data.activeSchools,
                    suspendedSchools = schoolsResult.data.suspendedSchools,
                    schools = schoolsResult.data.schools,
                    plans = planData?.plans ?: emptyMap(),
                    featureKeys = planData?.featureKeys ?: emptyList(),
                    error = if (plansResult is ApiResult.Failure) plansResult.message else null
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = schoolsResult.message)
        }
    }

    fun createSchool(name: String) = runSave("School created with a 30-day trial") { repository.createSchool(name.trim()) }

    fun setSchoolActive(schoolId: String, active: Boolean) =
        runSave(if (active) "School reactivated" else "School suspended") { repository.setSchoolActive(schoolId, active) }

    fun createSchoolAdmin(schoolId: String, email: String, lastName: String, firstName: String, middleInitial: String, suffix: String) =
        runSave("School administrator created") { repository.createSchoolAdmin(schoolId, email, lastName, firstName, middleInitial, suffix) }

    fun updateSubscription(
        schoolId: String,
        plan: String,
        subscriptionStatus: String,
        featureOverrides: Map<String, Boolean>,
        autoRenew: Boolean,
        cancelAtPeriodEnd: Boolean,
        startNewPeriod: Boolean,
        extendTrialDays: Int
    ) = runSave("Subscription and billing period updated") {
        repository.updateSubscription(
            schoolId, plan, subscriptionStatus, featureOverrides,
            autoRenew, cancelAtPeriodEnd, startNewPeriod, extendTrialDays
        )
    }

    fun reconcileSubscription(schoolId: String) =
        runSave("Subscription lifecycle checked") { repository.reconcileSubscription(schoolId) }

    fun loadInvoices(schoolId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(billingSchoolId = schoolId, billingLoading = true, error = null)
        val profile = repository.getBillingProfile(schoolId)
        val profileData = when (profile) {
            is ApiResult.Success -> profile.data
            is ApiResult.Failure -> null
        }
        val profileError = if (profile is ApiResult.Failure) profile.message else null
        val notices = repository.listGcashPaymentNotices(schoolId)
        val noticeData = when (notices) {
            is ApiResult.Success -> notices.data.paymentNotices
            is ApiResult.Failure -> emptyList()
        }
        when (val r = repository.listInvoices(schoolId)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                billingLoading = false,
                invoices = r.data.invoices,
                billingProfile = profileData,
                gcashPaymentNotices = noticeData,
                error = profileError ?: if (notices is ApiResult.Failure) notices.message else null
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                billingLoading = false,
                billingProfile = profileData,
                gcashPaymentNotices = noticeData,
                error = r.message
            )
        }
    }

    fun saveBillingProfile(schoolId: String, billingName: String, billingEmail: String, billingAddress: String, billingTaxId: String) =
        runBillingSave(schoolId, "Billing profile updated") {
            repository.updateBillingProfile(schoolId, billingName, billingEmail, billingAddress, billingTaxId)
        }

    fun createInvoice(schoolId: String, amountMinor: Long, dueAt: String?, note: String) =
        runBillingSave(schoolId, "Invoice created") { repository.createInvoice(schoolId, amountMinor, dueAt, note) }

    fun emailInvoice(schoolId: String, invoiceId: String, recipientEmail: String) =
        runBillingSave(schoolId, "Invoice emailed") { repository.emailInvoice(invoiceId, recipientEmail) }

    fun downloadInvoicePdf(invoice: MasterInvoiceItem) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = repository.downloadInvoicePdf(invoice.invoiceId)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    invoicePdf = InvoicePdfPayload(
                        fileName = (invoice.invoiceNumber.ifBlank { "PickupPass-Invoice" }) + ".pdf",
                        bytes = r.data
                    ),
                    message = "Invoice PDF ready to save"
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun clearInvoicePdf() { _uiState.value = _uiState.value.copy(invoicePdf = null) }

    fun markInvoicePaid(schoolId: String, invoiceId: String, reference: String, method: String, note: String) =
        runBillingSave(schoolId, "Invoice marked paid") { repository.markInvoicePaid(invoiceId, reference, method, note) }

    fun voidInvoice(schoolId: String, invoiceId: String, reason: String) =
        runBillingSave(schoolId, "Invoice voided") { repository.voidInvoice(invoiceId, reason) }

    fun reconcileOverdueInvoices(schoolId: String) =
        runBillingSave(schoolId, "Overdue invoices checked") { repository.reconcileOverdueInvoices(schoolId) }

    fun confirmGcashPayment(schoolId: String, noticeId: String, note: String) =
        runBillingSave(schoolId, "GCash payment confirmed") { repository.confirmGcashPaymentNotice(noticeId, note) }

    fun rejectGcashPayment(schoolId: String, noticeId: String, reason: String) =
        runBillingSave(schoolId, "GCash payment rejected") { repository.rejectGcashPaymentNotice(noticeId, reason) }

    private fun runBillingSave(schoolId: String, message: String, block: suspend () -> ApiResult<*>) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, message = message)
                    loadInvoices(schoolId)
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun signOut() { authRepository.signOut() }

    private fun runSave(message: String, block: suspend () -> ApiResult<*>) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, message = message)
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }
}
