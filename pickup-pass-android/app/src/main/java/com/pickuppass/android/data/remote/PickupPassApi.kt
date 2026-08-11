package com.pickuppass.android.data.remote

import com.pickuppass.android.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

interface PickupPassApi {

    @GET("session/me")
    suspend fun sessionMe(): Response<SessionStatusResponse>

    @POST("parent/generate-token")
    suspend fun generateToken(@Body body: GenerateTokenRequest): Response<PickupTokenResponse>

    @POST("parent/add-guardian")
    suspend fun addGuardian(@Body body: AddGuardianRequest): Response<AddGuardianResponse>

    @POST("parent/add-temporary-guardian")
    suspend fun addTemporaryGuardian(@Body body: AddTemporaryGuardianRequest): Response<AddGuardianResponse>

    @PUT("parent/guardian-schedule")
    suspend fun updateGuardianSchedule(@Body body: GuardianScheduleRequest): Response<GuardianScheduleResponse>

    @POST("parent/remove-guardian")
    suspend fun removeGuardian(@Body body: RemoveGuardianRequest): Response<SimpleStatusResponse>

    @GET("pickup/gates")
    suspend fun getActivePickupGates(): Response<ActivePickupGatesResponse>

    @POST("pickup/verify")
    suspend fun verifyPickup(@Body body: VerifyRequest): Response<VerifyResponse>

    @POST("pickup/approve")
    suspend fun approvePickup(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-Pickup-Gate-Id") pickupGateId: String?,
        @Body body: VerifyRequest
    ): Response<ApproveResponse>

    @POST("pickup/manual-override")
    suspend fun manualOverride(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-Pickup-Gate-Id") pickupGateId: String?,
        @Body body: ManualOverrideRequest
    ): Response<ManualOverrideResponse>

    @POST("teacher/register-parent")
    suspend fun registerParent(@Body body: RegisterParentRequest): Response<AddGuardianResponse>

    @POST("teacher/students")
    suspend fun createStudent(@Body body: CreateStudentRequest): Response<CreateStudentResponse>

    @POST("school-admin/staff")
    suspend fun inviteTeacher(@Body body: InviteTeacherRequest): Response<InviteTeacherResponse>

    @POST("device/register-token")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<SimpleStatusResponse>

    @POST("device/unregister-token")
    suspend fun unregisterDeviceToken(@Body body: DeviceTokenRequest): Response<SimpleStatusResponse>

    @Multipart
    @POST("school-admin/logo")
    suspend fun uploadSchoolLogo(@Part file: MultipartBody.Part): Response<LogoUploadResponse>

    @Multipart
    @POST("school-admin/students/import")
    suspend fun importStudents(
        @Part file: MultipartBody.Part,
        @Part("dryRun") dryRun: RequestBody
    ): Response<BulkStudentImportResponse>

    @POST("master-admin/schools/{schoolId}/status")
    suspend fun setSchoolStatus(
        @Path("schoolId") schoolId: String,
        @Body body: Map<String, String>
    ): Response<SimpleStatusResponse>

    @GET("school-admin/staff")
    suspend fun listStaff(): Response<StaffListResponse>

    @PUT("school-admin/staff/{uid}/sections")
    suspend fun updateTeacherSections(
        @Path("uid") uid: String,
        @Body body: UpdateSectionsRequest
    ): Response<SimpleStatusResponse>

    @PUT("school-admin/staff/{uid}/status")
    suspend fun setTeacherStatus(
        @Path("uid") uid: String,
        @Body body: StaffStatusRequest
    ): Response<SimpleStatusResponse>

    @POST("school-admin/staff/{uid}/revoke-sessions")
    suspend fun revokeTeacherSessions(@Path("uid") uid: String): Response<SimpleStatusResponse>

    @GET("school-admin/audit-events")
    suspend fun listAuditEvents(@retrofit2.http.Query("limit") limit: Int = 100): Response<AuditEventsResponse>

    @GET("school-admin/pickup-policy")
    suspend fun getPickupPolicy(): Response<PickupPolicyResponse>

    @PUT("school-admin/pickup-policy")
    suspend fun updatePickupPolicy(@Body body: UpdatePickupPolicyRequest): Response<PickupPolicyResponse>

    @GET("school-admin/dismissal-dashboard")
    suspend fun getDismissalDashboard(
        @retrofit2.http.Query("businessDate") businessDate: String? = null
    ): Response<DismissalDashboardResponse>


    @GET("academic-structure")
    suspend fun getAcademicStructure(): Response<AcademicStructureResponse>

    @POST("school-admin/academic-years")
    suspend fun createAcademicYear(@Body body: CreateAcademicYearRequest): Response<Map<String, Any?>>

    @PUT("school-admin/academic-years/{id}/current")
    suspend fun setCurrentAcademicYear(@Path("id") id: String): Response<Map<String, Any?>>

    @POST("school-admin/grade-sections")
    suspend fun createGradeSection(@Body body: CreateGradeSectionRequest): Response<Map<String, Any?>>

    @PUT("school-admin/grade-sections/{id}/status")
    suspend fun setGradeSectionStatus(
        @Path("id") id: String,
        @Body body: GradeSectionStatusRequest
    ): Response<Map<String, Any?>>


    @GET("school-admin/students/lifecycle")
    suspend fun listStudentLifecycle(
        @Query("status") status: String? = null
    ): Response<StudentLifecycleResponse>

    @PUT("school-admin/students/{studentId}/status")
    suspend fun updateStudentStatus(
        @Path("studentId") studentId: String,
        @Body body: StudentStatusRequest
    ): Response<SimpleStatusResponse>

    @POST("school-admin/students/promote")
    suspend fun promoteStudents(@Body body: PromotionRequest): Response<PromotionResponse>


    @GET("school-admin/reports/dismissals/summary")
    suspend fun getDismissalReportSummary(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("grade") grade: String? = null,
        @Query("section") section: String? = null
    ): Response<DismissalReportSummary>

    @Streaming
    @GET("school-admin/reports/dismissals/export")
    suspend fun exportDismissalReportCsv(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("grade") grade: String? = null,
        @Query("section") section: String? = null
    ): Response<ResponseBody>

    @GET("school-admin/guardian-verification")
    suspend fun listGuardianVerification(): Response<GuardianVerificationResponse>

    @PUT("school-admin/guardian-verification/policy")
    suspend fun updateGuardianVerificationPolicy(
        @Body body: GuardianVerificationPolicyRequest
    ): Response<Map<String, Any?>>

    @PUT("school-admin/guardian-verification/{guardianUid}")
    suspend fun updateGuardianVerificationStatus(
        @Path("guardianUid") guardianUid: String,
        @Body body: GuardianVerificationStatusRequest
    ): Response<GuardianVerificationStatusResponse>

    @GET("session/devices")
    suspend fun listDeviceSessions(): Response<DeviceSessionsResponse>

    @POST("session/devices/{deviceId}/revoke")
    suspend fun revokeDeviceSession(@Path("deviceId") deviceId: String): Response<DeviceRevokeResponse>

    @POST("session/devices/revoke-others")
    suspend fun revokeOtherDeviceSessions(): Response<DeviceRevokeResponse>

    @POST("school-admin/broadcasts")
    suspend fun broadcastToSchool(@Body body: BroadcastRequest): Response<BroadcastResponse>

    @POST("school-admin/broadcasts/schedule")
    suspend fun scheduleSchoolBroadcast(@Body body: ScheduleBroadcastRequest): Response<ScheduleBroadcastResponse>

    @GET("school-admin/broadcasts/history")
    suspend fun getSchoolBroadcastHistory(@Query("limit") limit: Int = 50): Response<BroadcastHistoryResponse>

    @retrofit2.http.DELETE("school-admin/broadcasts/{broadcastId}")
    suspend fun cancelSchoolBroadcast(@Path("broadcastId") broadcastId: String): Response<SimpleStatusResponse>

    @POST("teacher/broadcasts")
    suspend fun broadcastToSection(@Body body: BroadcastRequest): Response<BroadcastResponse>

    @GET("school-admin/campus-gates")
    suspend fun getCampusGates(): Response<CampusGateResponse>

    @POST("school-admin/campus-gates/campuses")
    suspend fun createCampus(@Body body: CreateCampusRequest): Response<Map<String, Any?>>

    @PUT("school-admin/campus-gates/campuses/{id}/status")
    suspend fun setCampusStatus(@Path("id") id: String, @Body body: ActiveStatusRequest): Response<Map<String, Any?>>

    @POST("school-admin/campus-gates/gates")
    suspend fun createPickupGate(@Body body: CreatePickupGateRequest): Response<Map<String, Any?>>

    @PUT("school-admin/campus-gates/gates/{id}/status")
    suspend fun setPickupGateStatus(@Path("id") id: String, @Body body: ActiveStatusRequest): Response<Map<String, Any?>>

    @GET("school-admin/staff-gates")
    suspend fun getStaffPickupGateAssignments(): Response<StaffPickupGateResponse>

    @PUT("school-admin/staff-gates/{uid}")
    suspend fun updateStaffPickupGates(
        @Path("uid") uid: String,
        @Body body: UpdateStaffPickupGatesRequest
    ): Response<Map<String, Any?>>



    // ---- Phase 3: master-admin SaaS console ----
    @GET("master-admin/schools")
    suspend fun listMasterSchools(): Response<MasterSchoolListResponse>

    @POST("master-admin/schools")
    suspend fun createMasterSchool(@Body body: CreateMasterSchoolRequest): Response<CreateMasterSchoolResponse>

    @POST("master-admin/schools/{schoolId}/staff")
    suspend fun createMasterSchoolStaff(
        @Path("schoolId") schoolId: String,
        @Body body: CreateMasterStaffRequest
    ): Response<CreateMasterStaffResponse>

    @GET("master-admin/plans")
    suspend fun getMasterPlanCatalog(): Response<MasterPlanCatalogResponse>

    @PUT("master-admin/schools/{schoolId}/subscription")
    suspend fun updateMasterSubscription(
        @Path("schoolId") schoolId: String,
        @Body body: UpdateMasterSubscriptionRequest
    ): Response<MasterSubscriptionResponse>

    @POST("master-admin/schools/{schoolId}/subscription/reconcile")
    suspend fun reconcileMasterSubscription(
        @Path("schoolId") schoolId: String
    ): Response<MasterSubscriptionResponse>

    @GET("master-admin/operations/overview")
    suspend fun getMasterOperationsOverview(): Response<MasterOperationsOverviewResponse>

    @POST("master-admin/operations/refresh")
    suspend fun refreshMasterOperations(): Response<MasterOperationsRefreshResponse>



    // ---- Phase 3: master-admin security center ----
    @GET("master-admin/security/overview")
    suspend fun getMasterSecurityOverview(@Query("limit") limit: Int = 100): Response<MasterSecurityOverviewResponse>

    @POST("master-admin/security/alerts/{alertId}/status")
    suspend fun setMasterSecurityAlertStatus(
        @Path("alertId") alertId: String,
        @Body body: MasterSecurityAlertStatusRequest
    ): Response<Map<String, String>>

    @POST("master-admin/security/users/{uid}/revoke-sessions")
    suspend fun revokeMasterUserSessions(
        @Path("uid") uid: String,
        @Body body: MasterSecurityRevokeSessionsRequest
    ): Response<Map<String, Any?>>

    @GET("master-admin/billing/schools/{schoolId}/profile")
    suspend fun getMasterBillingProfile(@Path("schoolId") schoolId: String): Response<MasterBillingProfileResponse>

    @PUT("master-admin/billing/schools/{schoolId}/profile")
    suspend fun updateMasterBillingProfile(
        @Path("schoolId") schoolId: String,
        @Body body: UpdateMasterBillingProfileRequest
    ): Response<MasterBillingProfileResponse>

    @GET("master-admin/billing/schools/{schoolId}/invoices")
    suspend fun listMasterInvoices(@Path("schoolId") schoolId: String): Response<MasterInvoiceListResponse>

    @POST("master-admin/billing/schools/{schoolId}/invoices")
    suspend fun createMasterInvoice(
        @Path("schoolId") schoolId: String,
        @Body body: CreateMasterInvoiceRequest
    ): Response<MasterInvoiceItem>

    @Streaming
    @GET("master-admin/billing/invoices/{invoiceId}/pdf")
    suspend fun downloadMasterInvoicePdf(@Path("invoiceId") invoiceId: String): Response<ResponseBody>

    @POST("master-admin/billing/invoices/{invoiceId}/email")
    suspend fun emailMasterInvoice(
        @Path("invoiceId") invoiceId: String,
        @Body body: EmailMasterInvoiceRequest
    ): Response<EmailMasterInvoiceResponse>

    @POST("master-admin/billing/invoices/{invoiceId}/paid")
    suspend fun markMasterInvoicePaid(
        @Path("invoiceId") invoiceId: String,
        @Body body: MarkMasterInvoicePaidRequest
    ): Response<MasterInvoiceItem>

    @POST("master-admin/billing/invoices/{invoiceId}/void")
    suspend fun voidMasterInvoice(
        @Path("invoiceId") invoiceId: String,
        @Body body: VoidMasterInvoiceRequest
    ): Response<MasterInvoiceItem>

    @POST("master-admin/billing/schools/{schoolId}/invoices/reconcile-overdue")
    suspend fun reconcileMasterOverdueInvoices(@Path("schoolId") schoolId: String): Response<Map<String, Int>>

    @GET("school-admin/data-export/status")
    suspend fun getSchoolDataExportStatus(): Response<SchoolDataExportStatusResponse>

    @Streaming
    @GET("school-admin/data-export/download")
    suspend fun downloadSchoolDataExport(): Response<ResponseBody>

    @GET("school-admin/billing")
    suspend fun getSchoolBillingCenter(): Response<SchoolBillingCenterResponse>

    @Streaming
    @GET("school-admin/billing/invoices/{invoiceId}/pdf")
    suspend fun downloadSchoolInvoicePdf(@Path("invoiceId") invoiceId: String): Response<ResponseBody>

    @Streaming
    @GET("school-admin/billing/invoices/{invoiceId}/receipt")
    suspend fun downloadSchoolReceiptPdf(@Path("invoiceId") invoiceId: String): Response<ResponseBody>

    @POST("school-admin/billing/invoices/{invoiceId}/gcash-payment-notice")
    suspend fun submitSchoolGcashPaymentNotice(
        @Path("invoiceId") invoiceId: String,
        @Body body: SubmitGcashPaymentNoticeRequest
    ): Response<GcashPaymentNoticeItem>

    @GET("master-admin/billing/schools/{schoolId}/payment-notices")
    suspend fun listMasterGcashPaymentNotices(
        @Path("schoolId") schoolId: String
    ): Response<GcashPaymentNoticeListResponse>

    @POST("master-admin/billing/payment-notices/{noticeId}/confirm")
    suspend fun confirmMasterGcashPaymentNotice(
        @Path("noticeId") noticeId: String,
        @Body body: GcashPaymentNoticeReviewRequest
    ): Response<GcashPaymentNoticeItem>

    @POST("master-admin/billing/payment-notices/{noticeId}/reject")
    suspend fun rejectMasterGcashPaymentNotice(
        @Path("noticeId") noticeId: String,
        @Body body: GcashPaymentNoticeReviewRequest
    ): Response<GcashPaymentNoticeItem>

    @PUT("master-admin/schools/{schoolId}/data-export-access")
    suspend fun setMasterSchoolDataExportAccess(
        @Path("schoolId") schoolId: String,
        @Body body: MasterDataExportAccessRequest
    ): Response<Map<String, Any?>>

    @Streaming
    @GET("master-admin/schools/{schoolId}/data-export")
    suspend fun downloadMasterSchoolDataExport(
        @Path("schoolId") schoolId: String
    ): Response<ResponseBody>

    // ---- Phase 3: master-admin disaster recovery ----
    @GET("master-admin/disaster-recovery/overview")
    suspend fun getMasterDisasterRecoveryOverview(): Response<MasterDisasterRecoveryOverviewResponse>

    @POST("master-admin/disaster-recovery/protection/free")
    suspend fun applyMasterFreeRecoveryProtection(
        @Body body: MasterApplyRecoveryProtectionRequest
    ): Response<Map<String, Any?>>

    @POST("master-admin/disaster-recovery/protection/startup")
    suspend fun applyMasterStartupRecoveryProtection(
        @Body body: MasterApplyRecoveryProtectionRequest
    ): Response<Map<String, Any?>>

    @POST("master-admin/disaster-recovery/protection/recommended")
    suspend fun applyMasterRecommendedRecoveryProtection(
        @Body body: MasterApplyRecoveryProtectionRequest
    ): Response<Map<String, Any?>>

    @POST("master-admin/disaster-recovery/recovery-drills")
    suspend fun startMasterRecoveryDrill(
        @Body body: MasterStartRecoveryDrillRequest
    ): Response<MasterRecoveryJobItem>

    @POST("master-admin/disaster-recovery/recovery-drills/{jobId}/refresh")
    suspend fun refreshMasterRecoveryDrill(
        @Path("jobId") jobId: String
    ): Response<MasterRecoveryJobItem>



    // ---- Phase 3: startup onboarding + launch readiness ----
    @GET("school-admin/launch-readiness")
    suspend fun getSchoolLaunchReadiness(): Response<LaunchReadinessResponse>

    @PUT("school-admin/launch-readiness/manual-checks")
    suspend fun updateSchoolLaunchManualChecks(
        @Body body: LaunchManualChecksRequest
    ): Response<LaunchReadinessResponse>

    @POST("school-admin/launch-readiness/request-review")
    suspend fun requestSchoolLaunchReview(): Response<LaunchReadinessResponse>

    @GET("master-admin/schools/{schoolId}/launch-readiness")
    suspend fun getMasterSchoolLaunchReadiness(
        @Path("schoolId") schoolId: String
    ): Response<LaunchReadinessResponse>

    @POST("master-admin/schools/{schoolId}/launch-readiness/approve")
    suspend fun approveMasterSchoolLaunch(
        @Path("schoolId") schoolId: String,
        @Body body: LaunchReviewDecisionRequest
    ): Response<LaunchReadinessResponse>

    @POST("master-admin/schools/{schoolId}/launch-readiness/reopen")
    suspend fun reopenMasterSchoolLaunch(
        @Path("schoolId") schoolId: String,
        @Body body: LaunchReviewDecisionRequest
    ): Response<LaunchReadinessResponse>

    @GET("tenant/entitlements")
    suspend fun getTenantEntitlements(): Response<TenantEntitlementsResponse>

}
