package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.AddGuardianRequest
import com.pickuppass.android.data.model.AddGuardianResponse
import com.pickuppass.android.data.model.RemoveGuardianRequest
import com.pickuppass.android.data.remote.PickupPassApi
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

@Singleton
class GuardianRepository @Inject constructor(
    private val api: PickupPassApi
) {
    suspend fun addGuardian(
        studentId: String,
        guardianName: String,
        guardianEmail: String,
        relationship: String
    ): ApiResult<AddGuardianResponse> {
        return try {
            val response = api.addGuardian(
                AddGuardianRequest(studentId, guardianEmail, guardianName, relationship)
            )
            val body = response.body()
            if (response.isSuccessful && body?.status != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(body?.error ?: "Could not add guardian")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun removeGuardian(studentId: String, guardianUid: String): ApiResult<Unit> {
        return try {
            val response = api.removeGuardian(RemoveGuardianRequest(studentId, guardianUid))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(response.body()?.error ?: "Could not remove guardian")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
