package com.pickuppass.android.data.remote

import com.pickuppass.android.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

interface PickupPassApi {

    @GET("session/me")
    suspend fun sessionMe(): Response<SessionStatusResponse>

    @POST("parent/generate-token")
    suspend fun generateToken(@Body body: GenerateTokenRequest): Response<PickupTokenResponse>

    @POST("parent/add-guardian")
    suspend fun addGuardian(@Body body: AddGuardianRequest): Response<AddGuardianResponse>

    @POST("parent/remove-guardian")
    suspend fun removeGuardian(@Body body: RemoveGuardianRequest): Response<SimpleStatusResponse>

    @POST("pickup/verify")
    suspend fun verifyPickup(@Body body: VerifyRequest): Response<VerifyResponse>

    @POST("pickup/approve")
    suspend fun approvePickup(@Header("Idempotency-Key") idempotencyKey: String, @Body body: VerifyRequest): Response<ApproveResponse>

    @POST("pickup/manual-override")
    suspend fun manualOverride(@Header("Idempotency-Key") idempotencyKey: String, @Body body: ManualOverrideRequest): Response<ManualOverrideResponse>

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

    @POST("school-admin/broadcasts")
    suspend fun broadcastToSchool(@Body body: BroadcastRequest): Response<BroadcastResponse>

    @POST("teacher/broadcasts")
    suspend fun broadcastToSection(@Body body: BroadcastRequest): Response<BroadcastResponse>
}
