package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.*
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

    suspend fun listTeachers(): ApiResult<List<TeacherWithSections>> {
        return try {
            val response = api.listStaff()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.teachers)
            } else {
                ApiResult.Failure("Could not load teachers")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateTeacherSections(uid: String, sections: List<TeacherSection>): ApiResult<Unit> {
        return try {
            val response = api.updateTeacherSections(uid, UpdateSectionsRequest(sections))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Could not save sections")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }


    suspend fun setTeacherActive(uid: String, active: Boolean): ApiResult<Unit> {
        return try {
            val response = api.setTeacherStatus(uid, StaffStatusRequest(active))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(response.body()?.error ?: "Could not update teacher status")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun revokeTeacherSessions(uid: String): ApiResult<Unit> {
        return try {
            val response = api.revokeTeacherSessions(uid)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(response.body()?.error ?: "Could not revoke sessions")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun listAuditEvents(limit: Int = 100): ApiResult<List<AuditEvent>> {
        return try {
            val response = api.listAuditEvents(limit)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.events)
            else ApiResult.Failure("Could not load audit log")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun broadcastToSchool(title: String, body: String, audience: List<String>): ApiResult<Int> {
        return try {
            val response = api.broadcastToSchool(BroadcastRequest(title = title, body = body, audience = audience))
            val respBody = response.body()
            if (response.isSuccessful && respBody != null) {
                ApiResult.Success(respBody.recipientCount)
            } else {
                ApiResult.Failure(respBody?.error ?: "Could not send announcement")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
