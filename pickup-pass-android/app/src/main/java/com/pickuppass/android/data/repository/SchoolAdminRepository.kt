package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.InviteTeacherRequest
import com.pickuppass.android.data.model.InviteTeacherResponse
import com.pickuppass.android.data.remote.PickupPassApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolAdminRepository @Inject constructor(
    private val api: PickupPassApi
) {
    suspend fun inviteTeacher(
        email: String,
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String
    ): ApiResult<InviteTeacherResponse> {
        return try {
            val response = api.inviteTeacher(
                InviteTeacherRequest(
                    email = email,
                    lastName = lastName,
                    firstName = firstName,
                    middleInitial = middleInitial.ifBlank { null },
                    suffix = suffix.ifBlank { null }
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.uid != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(body?.error ?: "Could not invite teacher")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
