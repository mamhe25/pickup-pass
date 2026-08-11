package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.*
import com.pickuppass.android.data.remote.PickupPassApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterAdminRepository @Inject constructor(private val api: PickupPassApi) {
    suspend fun listSchools(): ApiResult<MasterSchoolListResponse> = try {
        val response = api.listMasterSchools()
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not load schools")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun createSchool(name: String): ApiResult<CreateMasterSchoolResponse> = try {
        val response = api.createMasterSchool(CreateMasterSchoolRequest(name))
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not create school")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun setSchoolActive(schoolId: String, active: Boolean): ApiResult<Unit> = try {
        val response = api.setSchoolStatus(schoolId, mapOf("status" to if (active) "active" else "suspended"))
        if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Could not update school status")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun createSchoolAdmin(
        schoolId: String, email: String, lastName: String, firstName: String, middleInitial: String, suffix: String
    ): ApiResult<CreateMasterStaffResponse> = try {
        val response = api.createMasterSchoolStaff(
            schoolId,
            CreateMasterStaffRequest(
                email = email.trim(), lastName = lastName.trim(), firstName = firstName.trim(),
                middleInitial = middleInitial.trim().ifBlank { null }, suffix = suffix.trim().ifBlank { null }
            )
        )
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not create school administrator")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun getPlanCatalog(): ApiResult<MasterPlanCatalogResponse> = try {
        val response = api.getMasterPlanCatalog()
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not load subscription plans")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun updateSubscription(
        schoolId: String,
        plan: String,
        subscriptionStatus: String,
        featureOverrides: Map<String, Boolean>,
        autoRenew: Boolean,
        cancelAtPeriodEnd: Boolean,
        startNewPeriod: Boolean,
        extendTrialDays: Int
    ): ApiResult<MasterSubscriptionResponse> = try {
        val response = api.updateMasterSubscription(
            schoolId,
            UpdateMasterSubscriptionRequest(
                plan = plan,
                subscriptionStatus = subscriptionStatus,
                featureOverrides = featureOverrides,
                autoRenew = autoRenew,
                cancelAtPeriodEnd = cancelAtPeriodEnd,
                startNewPeriod = startNewPeriod,
                extendTrialDays = extendTrialDays
            )
        )
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not update subscription")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun reconcileSubscription(schoolId: String): ApiResult<MasterSubscriptionResponse> = try {
        val response = api.reconcileMasterSubscription(schoolId)
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not reconcile subscription")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun getBillingProfile(schoolId: String): ApiResult<MasterBillingProfileResponse> = try {
        val response = api.getMasterBillingProfile(schoolId)
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not load billing profile")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun updateBillingProfile(
        schoolId: String, billingName: String, billingEmail: String, billingAddress: String, billingTaxId: String
    ): ApiResult<MasterBillingProfileResponse> = try {
        val response = api.updateMasterBillingProfile(
            schoolId, UpdateMasterBillingProfileRequest(billingName, billingEmail, billingAddress, billingTaxId)
        )
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not update billing profile")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun listInvoices(schoolId: String): ApiResult<MasterInvoiceListResponse> = try {
        val response = api.listMasterInvoices(schoolId)
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not load billing records")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun createInvoice(schoolId: String, amountMinor: Long, dueAt: String?, note: String): ApiResult<MasterInvoiceItem> = try {
        val response = api.createMasterInvoice(schoolId, CreateMasterInvoiceRequest(amountMinor, "PHP", dueAt, note.ifBlank { null }))
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not create invoice")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun downloadInvoicePdf(invoiceId: String): ApiResult<ByteArray> = try {
        val response = api.downloadMasterInvoicePdf(invoiceId)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body.bytes())
        else ApiResult.Failure("Could not generate invoice PDF")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun emailInvoice(invoiceId: String, recipientEmail: String): ApiResult<EmailMasterInvoiceResponse> = try {
        val response = api.emailMasterInvoice(invoiceId, EmailMasterInvoiceRequest(recipientEmail.ifBlank { null }))
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not email invoice")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun markInvoicePaid(invoiceId: String, reference: String, method: String, note: String): ApiResult<MasterInvoiceItem> = try {
        val response = api.markMasterInvoicePaid(invoiceId, MarkMasterInvoicePaidRequest(reference.ifBlank { null }, method.ifBlank { null }, note.ifBlank { null }))
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not mark invoice paid")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun voidInvoice(invoiceId: String, reason: String): ApiResult<MasterInvoiceItem> = try {
        val response = api.voidMasterInvoice(invoiceId, VoidMasterInvoiceRequest(reason.ifBlank { null }))
        response.body()?.takeIf { response.isSuccessful }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure("Could not void invoice")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun reconcileOverdueInvoices(schoolId: String): ApiResult<Unit> = try {
        val response = api.reconcileMasterOverdueInvoices(schoolId)
        if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Could not reconcile overdue invoices")
    } catch (e: Exception) { ApiResult.Failure(e.message ?: "Network error") }

    suspend fun listGcashPaymentNotices(schoolId: String): ApiResult<GcashPaymentNoticeListResponse> = try {
        val response = api.listMasterGcashPaymentNotices(schoolId)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("Could not load GCash payment notices")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun confirmGcashPaymentNotice(noticeId: String, note: String): ApiResult<GcashPaymentNoticeItem> = try {
        val response = api.confirmMasterGcashPaymentNotice(noticeId, GcashPaymentNoticeReviewRequest(note.ifBlank { null }))
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("Could not confirm GCash payment")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun rejectGcashPaymentNotice(noticeId: String, reason: String): ApiResult<GcashPaymentNoticeItem> = try {
        val response = api.rejectMasterGcashPaymentNotice(noticeId, GcashPaymentNoticeReviewRequest(reason))
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("Could not reject GCash payment")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

}
