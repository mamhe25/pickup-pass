package com.pickuppass.android.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
    suspend fun getPickupPolicy(): ApiResult<PickupPolicyResponse> {
        return try {
            val response = api.getPickupPolicy()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load pickup policy")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updatePickupPolicy(
        mode: String,
        earliestPickupTime: String?,
        latestPickupTime: String?,
        allowManualOverride: Boolean
    ): ApiResult<PickupPolicyResponse> {
        return try {
            val response = api.updatePickupPolicy(
                UpdatePickupPolicyRequest(
                    mode = mode,
                    earliestPickupTime = earliestPickupTime,
                    latestPickupTime = latestPickupTime,
                    allowManualOverride = allowManualOverride
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure(body?.let { null } ?: "Could not save pickup policy")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getDismissalDashboard(businessDate: String? = null): ApiResult<DismissalDashboardResponse> {
        return try {
            val response = api.getDismissalDashboard(businessDate)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load dismissal dashboard")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getAcademicStructure(): ApiResult<AcademicStructureResponse> {
        return try {
            val response = api.getAcademicStructure()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load academic structure")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createAcademicYear(name: String, startDate: String, endDate: String, current: Boolean): ApiResult<Unit> {
        return try {
            val response = api.createAcademicYear(CreateAcademicYearRequest(name, startDate, endDate, current))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not create academic year")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setCurrentAcademicYear(id: String): ApiResult<Unit> {
        return try {
            val response = api.setCurrentAcademicYear(id)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not change current academic year")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createGradeSection(academicYearId: String, gradeLevel: String, sectionName: String): ApiResult<Unit> {
        return try {
            val response = api.createGradeSection(CreateGradeSectionRequest(academicYearId, gradeLevel, sectionName))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not create grade/section")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setGradeSectionActive(id: String, active: Boolean): ApiResult<Unit> {
        return try {
            val response = api.setGradeSectionStatus(id, GradeSectionStatusRequest(active))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not update grade/section")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }


    suspend fun importStudents(
        bytes: ByteArray,
        filename: String,
        dryRun: Boolean
    ): ApiResult<BulkStudentImportResponse> {
        return try {
            val contentType = when {
                filename.lowercase().endsWith(".csv") -> "text/csv"
                filename.lowercase().endsWith(".xls") -> "application/vnd.ms-excel"
                else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }.toMediaType()
            val fileBody = bytes.toRequestBody(contentType)
            val filePart = MultipartBody.Part.createFormData("file", filename, fileBody)
            val dryRunBody = dryRun.toString().toRequestBody("text/plain".toMediaType())
            val response = api.importStudents(filePart, dryRunBody)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(body?.error ?: "Could not process student import")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
