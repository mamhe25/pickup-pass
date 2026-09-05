package com.pickuppass.android.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.pickuppass.android.data.model.*
import com.pickuppass.android.data.remote.PickupPassApi
import org.json.JSONObject
import retrofit2.Response
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

    suspend fun scheduleBroadcast(
        title: String,
        body: String,
        audience: List<String>,
        scheduledAt: String
    ): ApiResult<ScheduleBroadcastResponse> {
        return try {
            val response = api.scheduleSchoolBroadcast(
                ScheduleBroadcastRequest(title, body, audience, scheduledAt)
            )
            val payload = response.body()
            if (response.isSuccessful && payload != null) ApiResult.Success(payload)
            else ApiResult.Failure(payload?.error ?: "Could not schedule announcement")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getBroadcastHistory(): ApiResult<List<BroadcastHistoryItem>> {
        return try {
            val response = api.getSchoolBroadcastHistory()
            val payload = response.body()
            if (response.isSuccessful && payload != null) ApiResult.Success(payload.broadcasts)
            else ApiResult.Failure("Could not load announcement history")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun cancelScheduledBroadcast(id: String): ApiResult<Unit> {
        return try {
            val response = api.cancelSchoolBroadcast(id)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(response.body()?.error ?: "Could not cancel announcement")
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

    suspend fun getDismissalDashboard(
        businessDate: String? = null
    ): ApiResult<DismissalDashboardResponse> {
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
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(apiError(response, "Could not load academic structure"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createAcademicYear(
        name: String,
        startDate: String,
        endDate: String,
        current: Boolean
    ): ApiResult<Unit> {
        return try {
            val response = api.createAcademicYear(
                CreateAcademicYearRequest(name, startDate, endDate, current)
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not create academic year"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateAcademicYear(
        id: String,
        name: String,
        startDate: String,
        endDate: String
    ): ApiResult<Unit> {
        return try {
            val response = api.updateAcademicYear(
                id,
                UpdateAcademicYearRequest(
                    name = name,
                    startDate = startDate,
                    endDate = endDate
                )
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not update academic year"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setCurrentAcademicYear(id: String): ApiResult<Unit> {
        return try {
            val response = api.setCurrentAcademicYear(id)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not change current academic year"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setAcademicYearActive(id: String, active: Boolean): ApiResult<Unit> {
        return try {
            val response = api.setAcademicYearStatus(
                id,
                AcademicYearStatusRequest(active)
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(
                    apiError(
                        response,
                        if (active) "Could not reactivate academic year"
                        else "Could not archive academic year"
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteAcademicYear(id: String): ApiResult<Unit> {
        return try {
            val response = api.deleteAcademicYear(id)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not delete academic year"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createGradeSection(
        academicYearId: String,
        gradeLevel: String,
        sectionName: String
    ): ApiResult<Unit> {
        return try {
            val response = api.createGradeSection(
                CreateGradeSectionRequest(academicYearId, gradeLevel, sectionName)
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not create grade/section"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateGradeSection(
        id: String,
        gradeLevel: String,
        sectionName: String
    ): ApiResult<Unit> {
        return try {
            val response = api.updateGradeSection(
                id,
                UpdateGradeSectionRequest(
                    gradeLevel = gradeLevel,
                    sectionName = sectionName
                )
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not update grade/section"))
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setGradeSectionActive(id: String, active: Boolean): ApiResult<Unit> {
        return try {
            val response = api.setGradeSectionStatus(
                id,
                GradeSectionStatusRequest(active)
            )
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(
                    apiError(
                        response,
                        if (active) "Could not reactivate grade/section"
                        else "Could not archive grade/section"
                    )
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteGradeSection(id: String): ApiResult<Unit> {
        return try {
            val response = api.deleteGradeSection(id)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(apiError(response, "Could not delete grade/section"))
            }
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

    suspend fun getDismissalReportSummary(
        from: String,
        to: String,
        grade: String? = null,
        section: String? = null
    ): ApiResult<DismissalReportSummary> {
        return try {
            val response = api.getDismissalReportSummary(from, to, grade, section)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load dismissal report")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun exportDismissalReportCsv(
        from: String,
        to: String,
        grade: String? = null,
        section: String? = null
    ): ApiResult<ByteArray> {
        return try {
            val response = api.exportDismissalReportCsv(from, to, grade, section)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.bytes())
            else ApiResult.Failure("Could not export dismissal report")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun listStudentLifecycle(
        status: String? = null
    ): ApiResult<StudentLifecycleResponse> {
        return try {
            val response = api.listStudentLifecycle(status)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load student lifecycle records")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateStudentStatus(
        studentId: String,
        status: String,
        reason: String
    ): ApiResult<Unit> {
        return try {
            val response = api.updateStudentStatus(
                studentId,
                StudentStatusRequest(status, reason)
            )
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(response.body()?.error ?: "Could not update student status")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun previewPromotion(targetAcademicYearId: String): ApiResult<PromotionResponse> {
        return promoteStudents(targetAcademicYearId, dryRun = true)
    }

    suspend fun executePromotion(targetAcademicYearId: String): ApiResult<PromotionResponse> {
        return promoteStudents(targetAcademicYearId, dryRun = false)
    }

    private suspend fun promoteStudents(
        targetAcademicYearId: String,
        dryRun: Boolean
    ): ApiResult<PromotionResponse> {
        return try {
            val response = api.promoteStudents(
                PromotionRequest(
                    targetAcademicYearId = targetAcademicYearId,
                    dryRun = dryRun
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure(body?.error ?: "Could not process student promotion")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun listGuardianVerification(): ApiResult<GuardianVerificationResponse> {
        return try {
            val response = api.listGuardianVerification()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load guardian verification records")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateGuardianVerificationPolicy(required: Boolean): ApiResult<Unit> {
        return try {
            val response = api.updateGuardianVerificationPolicy(
                GuardianVerificationPolicyRequest(required)
            )
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not update guardian verification policy")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateGuardianVerificationStatus(
        uid: String,
        status: String,
        reason: String
    ): ApiResult<GuardianVerificationStatusResponse> {
        return try {
            val response = api.updateGuardianVerificationStatus(
                uid,
                GuardianVerificationStatusRequest(status, reason)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not update guardian verification status")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getCampusGates(): ApiResult<CampusGateResponse> {
        return try {
            val response = api.getCampusGates()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load campuses and gates")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createCampus(
        name: String,
        address: String
    ): ApiResult<Map<String, Any?>> {
        return try {
            val response = api.createCampus(CreateCampusRequest(name, address))
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not create campus")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setCampusActive(
        id: String,
        active: Boolean
    ): ApiResult<Map<String, Any?>> {
        return try {
            val response = api.setCampusStatus(id, ActiveStatusRequest(active))
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not update campus")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun createPickupGate(
        campusId: String,
        name: String,
        description: String
    ): ApiResult<Map<String, Any?>> {
        return try {
            val response = api.createPickupGate(
                CreatePickupGateRequest(campusId, name, description)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not create pickup gate")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setPickupGateActive(
        id: String,
        active: Boolean
    ): ApiResult<Map<String, Any?>> {
        return try {
            val response = api.setPickupGateStatus(id, ActiveStatusRequest(active))
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not update pickup gate")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getStaffPickupGateAssignments(): ApiResult<StaffPickupGateResponse> {
        return try {
            val response = api.getStaffPickupGateAssignments()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load staff pickup-gate assignments")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun updateStaffPickupGates(
        uid: String,
        gateIds: List<String>
    ): ApiResult<Unit> {
        return try {
            val response = api.updateStaffPickupGates(
                uid,
                UpdateStaffPickupGatesRequest(gateIds)
            )
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Could not save pickup-gate assignment")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getBillingCenter(): ApiResult<SchoolBillingCenterResponse> = try {
        val response = api.getSchoolBillingCenter()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("Could not load billing center")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun submitGcashPaymentNotice(
        invoiceId: String,
        payerName: String,
        referenceNumber: String,
        paidAt: String?,
        note: String
    ): ApiResult<GcashPaymentNoticeItem> = try {
        val response = api.submitSchoolGcashPaymentNotice(
            invoiceId,
            SubmitGcashPaymentNoticeRequest(
                payerName = payerName,
                referenceNumber = referenceNumber,
                paidAt = paidAt,
                note = note.ifBlank { null }
            )
        )
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(
                response.errorBody()?.string()?.take(300)
                    ?: "Could not submit GCash payment notice"
            )
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun downloadBillingInvoicePdf(
        invoiceId: String
    ): ApiResult<ByteArray> = try {
        val response = api.downloadSchoolInvoicePdf(invoiceId)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body.bytes())
        else ApiResult.Failure("Could not download invoice PDF")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun downloadBillingReceiptPdf(
        invoiceId: String
    ): ApiResult<ByteArray> = try {
        val response = api.downloadSchoolReceiptPdf(invoiceId)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body.bytes())
        else ApiResult.Failure("Could not download payment receipt")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun getSchoolDataExportStatus(): ApiResult<SchoolDataExportStatusResponse> = try {
        val response = api.getSchoolDataExportStatus()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else ApiResult.Failure("Could not load data-export status")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun downloadSchoolDataExport(): ApiResult<ByteArray> = try {
        val response = api.downloadSchoolDataExport()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body.bytes())
        } else {
            ApiResult.Failure(
                response.errorBody()?.string()?.take(300)
                    ?: "Could not create school data export"
            )
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun getLaunchReadiness(): ApiResult<LaunchReadinessResponse> = try {
        val response = api.getSchoolLaunchReadiness()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(
                response.errorBody()?.string()?.take(300)
                    ?: "Could not load launch readiness"
            )
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun updateLaunchManualChecks(
        checks: Map<String, Boolean>
    ): ApiResult<LaunchReadinessResponse> = try {
        val response = api.updateSchoolLaunchManualChecks(
            LaunchManualChecksRequest(checks)
        )
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(
                response.errorBody()?.string()?.take(300)
                    ?: "Could not update launch checks"
            )
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun requestLaunchReview(): ApiResult<LaunchReadinessResponse> = try {
        val response = api.requestSchoolLaunchReview()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(
                response.errorBody()?.string()?.take(300)
                    ?: "Could not request launch review"
            )
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    /**
     * Extracts backend business-rule messages (especially 409 conflict
     * responses) so destructive academic actions fail with a useful reason
     * instead of a generic network message.
     */
    private fun apiError(
        response: Response<*>,
        fallback: String
    ): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        if (raw.isNullOrBlank()) return fallback

        return try {
            JSONObject(raw).optString("error").ifBlank { fallback }
        } catch (_: Exception) {
            raw.take(240).ifBlank { fallback }
        }
    }
}
