package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.AddGuardianRequest
import com.pickuppass.android.data.model.AddGuardianResponse
import com.pickuppass.android.data.model.AddTemporaryGuardianRequest
import com.pickuppass.android.data.model.RemoveGuardianRequest
import com.pickuppass.android.data.model.GuardianScheduleRequest
import com.pickuppass.android.data.model.GuardianScheduleResponse
import com.pickuppass.android.data.model.UserProfile
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
    suspend fun getGuardianProfiles(studentId: String): ApiResult<Map<String, UserProfile>> {
        return try {
            val response = api.getGuardianProfiles(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.guardians.associate { summary ->
                    summary.uid to UserProfile(
                        uid = summary.uid,
                        displayName = summary.displayName,
                        photoUrl = summary.photoUrl
                    )
                })
            } else {
                ApiResult.Failure("Could not load guardian profiles")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun addGuardian(
        studentId: String,
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        guardianEmail: String,
        relationship: String
    ): ApiResult<AddGuardianResponse> {
        return try {
            val response = api.addGuardian(
                AddGuardianRequest(
                    studentId = studentId,
                    guardianEmail = guardianEmail,
                    lastName = lastName,
                    firstName = firstName,
                    middleInitial = middleInitial.ifBlank { null },
                    suffix = suffix.ifBlank { null },
                    relationship = relationship
                )
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

    suspend fun addTemporaryGuardian(
        studentId: String,
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        guardianEmail: String,
        relationship: String,
        validDate: String
    ): ApiResult<AddGuardianResponse> {
        return try {
            val response = api.addTemporaryGuardian(
                AddTemporaryGuardianRequest(
                    studentId = studentId,
                    guardianEmail = guardianEmail,
                    lastName = lastName,
                    firstName = firstName,
                    middleInitial = middleInitial.ifBlank { null },
                    suffix = suffix.ifBlank { null },
                    relationship = relationship,
                    validDate = validDate
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.status != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(body?.error ?: "Could not authorize temporary guardian")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }


    suspend fun updateGuardianSchedule(
        studentId: String,
        guardianUid: String,
        enabled: Boolean,
        pickupDays: List<String>,
        startDate: String,
        endDate: String
    ): ApiResult<GuardianScheduleResponse> {
        return try {
            val response = api.updateGuardianSchedule(
                GuardianScheduleRequest(studentId, guardianUid, enabled, pickupDays, startDate, endDate)
            )
            val body = response.body()
            if (response.isSuccessful && body?.status == "updated") ApiResult.Success(body)
            else ApiResult.Failure(body?.error ?: "Could not update pickup schedule")
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
