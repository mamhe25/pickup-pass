package com.pickuppass.android.ui.masteradmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.MasterPlanDefinition
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.data.model.MasterInvoiceItem
import com.pickuppass.android.data.model.MasterBillingProfileResponse
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.model.MasterOperationsOverviewResponse
import com.pickuppass.android.data.model.MasterSecurityOverviewResponse
import com.pickuppass.android.data.model.MasterDisasterRecoveryOverviewResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.MasterAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoicePdfPayload(val fileName: String, val bytes: ByteArray)
data class DataExportZipPayload(val fileName: String, val bytes: ByteArray)

data class MasterAdminUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val totalSchools: Int = 0,
    val activeSchools: Int = 0,
    val suspendedSchools: Int = 0,
    val schools: List<MasterSchoolItem> = emptyList(),
    val operationsLoading: Boolean = false,
    val operations: MasterOperationsOverviewResponse? = null,
    val securityLoading: Boolean = false,
    val security: MasterSecurityOverviewResponse? = null,
    val disasterRecoveryLoading: Boolean = false,
    val disasterRecovery: MasterDisasterRecoveryOverviewResponse? = null,
    val plans: Map<String, MasterPlanDefinition> = emptyMap(),
    val featureKeys: List<String> = emptyList(),
    val billingSchoolId: String? = null,
    val billingLoading: Boolean = false,
    val invoices: List<MasterInvoiceItem> = emptyList(),
    val billingProfile: MasterBillingProfileResponse? = null,
    val gcashPaymentNotices: List<GcashPaymentNoticeItem> = emptyList(),
    val invoicePdf: InvoicePdfPayload? = null,
    val dataExportZip: DataExportZipPayload? = null,
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
        val operationsResult = repository.getOperationsOverview()
        val securityResult = repository.getSecurityOverview()
        val disasterRecoveryResult = repository.getDisasterRecoveryOverview()
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
                    operations = when (operationsResult) {
                        is ApiResult.Success -> operationsResult.data
                        is ApiResult.Failure -> _uiState.value.operations
                    },
                    security = when (securityResult) {
                        is ApiResult.Success -> securityResult.data
                        is ApiResult.Failure -> _uiState.value.security
                    },
                    disasterRecovery = when (disasterRecoveryResult) {
                        is ApiResult.Success -> disasterRecoveryResult.data
                        is ApiResult.Failure -> _uiState.value.disasterRecovery
                    },
                    plans = planData?.plans ?: emptyMap(),
                    featureKeys = planData?.featureKeys ?: emptyList(),
                    error = when {
                        plansResult is ApiResult.Failure -> plansResult.message
                        operationsResult is ApiResult.Failure -> operationsResult.message
                        securityResult is ApiResult.Failure -> securityResult.message
                        disasterRecoveryResult is ApiResult.Failure -> disasterRecoveryResult.message
                        else -> null
                    }
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = schoolsResult.message)
        }
    }

    fun loadOperations(quiet: Boolean = false) = viewModelScope.launch {
        if (!quiet) _uiState.value = _uiState.value.copy(operationsLoading = true, error = null)
        when (val r = repository.getOperationsOverview()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                operationsLoading = false,
                operations = r.data
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                operationsLoading = false,
                error = if (quiet) _uiState.value.error else r.message
            )
        }
    }

    fun refreshOperations() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, operationsLoading = true, error = null, message = null)
            when (val r = repository.refreshOperations()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        operationsLoading = false,
                        message = "Operations health refreshed"
                    )
                    load()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    operationsLoading = false,
                    error = r.message
                )
            }
        }
    }


    fun loadSecurity(quiet: Boolean = false) = viewModelScope.launch {
        if (!quiet) _uiState.value = _uiState.value.copy(securityLoading = true, error = null)
        when (val r = repository.getSecurityOverview()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(securityLoading = false, security = r.data)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                securityLoading = false, error = if (quiet) _uiState.value.error else r.message
            )
        }
    }

    fun acknowledgeSecurityAlert(alertId: String) = runSecuritySave("Security alert acknowledged") {
        repository.setSecurityAlertStatus(alertId, "acknowledged", "Reviewed by master admin")
    }

    fun resolveSecurityAlert(alertId: String, note: String) = runSecuritySave("Security alert resolved") {
        repository.setSecurityAlertStatus(alertId, "resolved", note)
    }

    fun revokeSecurityUserSessions(uid: String, reason: String) = runSecuritySave("User sessions revoked") {
        repository.revokeUserSessions(uid, reason)
    }

    private fun runSecuritySave(message: String, block: suspend () -> ApiResult<*>) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, message = message)
                    loadSecurity(quiet = true)
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun loadDisasterRecovery(quiet: Boolean = false) = viewModelScope.launch {
        if (!quiet) _uiState.value = _uiState.value.copy(disasterRecoveryLoading = true, error = null)
        when (val r = repository.getDisasterRecoveryOverview()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                disasterRecoveryLoading = false, disasterRecovery = r.data
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                disasterRecoveryLoading = false, error = if (quiet) _uiState.value.error else r.message
            )
        }
    }

    fun applyFreeRecoveryProtection() = runDisasterRecoverySave("Free Firestore safeguards requested") {
        repository.applyFreeRecoveryProtection()
    }

    fun applyStartupRecoveryProtection() = runDisasterRecoverySave("Startup backup profile requested; refresh status shortly") {
        repository.applyStartupRecoveryProtection()
    }

    fun applyRecommendedRecoveryProtection() = runDisasterRecoverySave("Growth recovery protection requested; refresh status shortly") {
        repository.applyRecommendedRecoveryProtection()
    }

    fun startRecoveryDrill(backupName: String, reason: String, confirmationText: String) =
        runDisasterRecoverySave("Isolated recovery drill started") {
            repository.startRecoveryDrill(backupName, reason, confirmationText)
        }

    fun refreshRecoveryDrill(jobId: String) = runDisasterRecoverySave("Recovery-drill status refreshed") {
        repository.refreshRecoveryDrill(jobId)
    }

    private fun runDisasterRecoverySave(message: String, block: suspend () -> ApiResult<*>) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, disasterRecoveryLoading = true, error = null, message = null)
            when (val r = block()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, disasterRecoveryLoading = false, message = message)
                    loadDisasterRecovery(quiet = true)
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    saving = false, disasterRecoveryLoading = false, error = r.message
                )
            }
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

    fun setSchoolDataExportAccess(schoolId: String, enabled: Boolean) =
        runSave(if (enabled) "School self-service data export enabled" else "School self-service data export disabled") {
            repository.setSchoolDataExportAccess(schoolId, enabled)
        }

    fun downloadSchoolDataExport(school: MasterSchoolItem) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val r = repository.downloadSchoolDataExport(school.schoolId)) {
                is ApiResult.Success -> {
                    val safeName = school.schoolName.ifBlank { "School" }
                        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                        .take(50)
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        dataExportZip = DataExportZipPayload(
                            fileName = "PickupPass_${safeName}_Data_Export.zip",
                            bytes = r.data
                        ),
                        message = "School data export ready to save"
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun clearDataExportZip() { _uiState.value = _uiState.value.copy(dataExportZip = null) }

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
                    loadOperations(quiet = true)
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
