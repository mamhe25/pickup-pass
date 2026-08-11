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

}
