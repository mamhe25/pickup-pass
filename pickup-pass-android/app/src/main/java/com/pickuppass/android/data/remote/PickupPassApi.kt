package com.pickuppass.android.data.remote

import com.pickuppass.android.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PickupPassApi {

    @POST("parent/generate-token")
    suspend fun generateToken(@Body body: GenerateTokenRequest): Response<PickupTokenResponse>

    @POST("parent/add-guardian")
    suspend fun addGuardian(@Body body: AddGuardianRequest): Response<AddGuardianResponse>

    @POST("parent/remove-guardian")
    suspend fun removeGuardian(@Body body: RemoveGuardianRequest): Response<SimpleStatusResponse>

    @POST("pickup/verify")
    suspend fun verifyPickup(@Body body: VerifyRequest): Response<VerifyResponse>

    @POST("pickup/approve")
    suspend fun approvePickup(@Body body: VerifyRequest): Response<ApproveResponse>

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
}
