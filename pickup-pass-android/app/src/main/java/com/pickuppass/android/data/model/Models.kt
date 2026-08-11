package com.pickuppass.android.data.model

import com.google.firebase.firestore.PropertyName

/** Firestore: students/{studentId} */
data class Student(
    var id: String = "",
    val fullName: String = "",
    val grade: String = "",
    val section: String = "",
    val schoolId: String = "",
    val photoUrl: String? = null,
    val status: String = "active",
    val academicYearId: String = "",
    val academicYearName: String = "",
    val gradeSectionId: String = "",
    val studentNumber: String = "",
    @get:PropertyName("guardianUids") @set:PropertyName("guardianUids")
    var guardianUids: List<String> = emptyList(),
    @get:PropertyName("guardians") @set:PropertyName("guardians")
    var guardians: Map<String, GuardianEntry> = emptyMap()
)

data class GuardianEntry(
    val relationship: String = "",
    val isPrimary: Boolean = false,
    val addedBy: String = "",
)

/** Firestore: notifications/{notificationId} */
data class NotificationItem(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "",
    val studentId: String? = null,
    val senderName: String? = null, // only set for type == "broadcast"
    val read: Boolean = false,
    val createdAtMillis: Long? = null,
)

/** Firestore: exitLogs/{logId}, enriched client-side with student/guardian/staff display info. */
data class ExitLogEntry(
    val id: String = "",
    val studentName: String = "",
    val grade: String = "",
    val section: String = "",
    val guardianName: String = "",
    val staffName: String = "",
    val timestampMillis: Long? = null,
)

data class PickupPolicyInfo(
    val mode: String = "unrestricted",
    val earliestPickupTime: String = "",
    val latestPickupTime: String = "",
    val allowManualOverride: Boolean = true
)

/** Firestore: schools/{schoolId} */
data class SchoolInfo(
    var id: String = "",
    val schoolName: String = "",
    val status: String = "",
    val logoUrl: String? = null,
    val pickupPolicy: PickupPolicyInfo? = null,
)

/** Firestore: users/{uid} */
data class UserProfile(
    var uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "",
    val schoolId: String = "",
    val isActive: Boolean = true,
)

// ---- Backend API request/response bodies (see Spring Boot controllers) ----

data class GenerateTokenRequest(val studentId: String)

data class PickupTokenResponse(
    val qrToken: String,
    val expiresAt: String,   // ISO-8601
    val dismissalDeadline: String
)

data class VerifyRequest(val qrToken: String)

data class VerifyResponse(
    val valid: Boolean,
    val studentId: String? = null,
    val parentUid: String? = null,
    val reason: String? = null
)

data class ApproveResponse(
    val status: String? = null,
    val valid: Boolean = true,
    val reason: String? = null
)

data class AddGuardianRequest(
    val studentId: String,
    val guardianEmail: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null,
    val relationship: String
)

data class AddGuardianResponse(
    val guardianUid: String? = null,
    val status: String? = null,
    val emailSent: Boolean = true,
    val error: String? = null
)

data class RemoveGuardianRequest(val studentId: String, val guardianUid: String)

data class SimpleStatusResponse(val status: String? = null, val error: String? = null)

data class RegisterParentRequest(
    val studentId: String,
    val parentEmail: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null,
    val relationship: String? = null
)

data class CreateStudentRequest(
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null,
    val grade: String,
    val section: String
)

data class CreateStudentResponse(
    val studentId: String? = null,
    val fullName: String? = null,
    val error: String? = null
)

data class InviteTeacherRequest(
    val email: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null
)

data class InviteTeacherResponse(
    val uid: String? = null,
    val role: String? = null,
    val emailSent: Boolean = true,
    val error: String? = null
)

data class DeviceTokenRequest(val token: String)

data class LogoUploadResponse(val logoUrl: String? = null, val error: String? = null)

/** Grade/section pair a teacher is assigned to — scopes their broadcast reach. */
data class TeacherSection(val grade: String, val section: String)

/** Firestore: users/{uid}, teacher role — as returned by GET /school-admin/staff for the section-editor UI. */
data class TeacherWithSections(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val assignedSections: List<TeacherSection> = emptyList(),
    val isActive: Boolean = true
)

data class StaffListResponse(val teachers: List<TeacherWithSections> = emptyList())

data class UpdateSectionsRequest(val sections: List<TeacherSection>)

data class BroadcastRequest(
    val title: String,
    val body: String,
    val audience: List<String>? = null // only used by the school-admin endpoint; omitted for teacher broadcasts
)

data class BroadcastResponse(
    val recipientCount: Int = 0,
    val error: String? = null
)


data class StaffStatusRequest(val active: Boolean)

data class ManualOverrideRequest(
    val studentId: String,
    val guardianUid: String,
    val reason: String
)

data class ManualOverrideResponse(
    val status: String? = null,
    val method: String? = null,
    val exitLogId: String? = null,
    val error: String? = null
)

data class AuditEvent(
    val id: String = "",
    val actorUid: String = "",
    val actorRole: String = "",
    val action: String = "",
    val resourceType: String = "",
    val resourceId: String = "",
    val timestamp: String? = null,
    val details: Map<String, Any?> = emptyMap()
)

data class AuditEventsResponse(val events: List<AuditEvent> = emptyList())


data class SessionStatusResponse(
    val uid: String = "",
    val role: String = "",
    val schoolId: String? = null,
    val status: String = ""
)

// ---- Phase 2: school pickup policy + live dismissal dashboard ----

data class PickupPolicyResponse(
    val mode: String = "unrestricted",
    val earliestPickupTime: String = "",
    val latestPickupTime: String = "",
    val allowManualOverride: Boolean = true,
    val timeZone: String = "Asia/Manila"
)

data class UpdatePickupPolicyRequest(
    val mode: String,
    val earliestPickupTime: String? = null,
    val latestPickupTime: String? = null,
    val allowManualOverride: Boolean = true
)

data class DashboardStudent(
    val studentId: String = "",
    val studentName: String = "",
    val grade: String = "",
    val section: String = ""
)

data class DashboardRelease(
    val exitLogId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val grade: String = "",
    val section: String = "",
    val guardianUid: String = "",
    val guardianName: String = "",
    val staffName: String = "",
    val method: String = "qr_scan",
    val timestamp: String? = null
)

data class DismissalDashboardResponse(
    val businessDate: String = "",
    val timeZone: String = "Asia/Manila",
    val totalStudents: Int = 0,
    val releasedCount: Int = 0,
    val remainingCount: Int = 0,
    val releaseRatePercent: Double = 0.0,
    val recentReleases: List<DashboardRelease> = emptyList(),
    val remainingStudents: List<DashboardStudent> = emptyList(),
    val remainingTruncated: Boolean = false
)

// ---- Phase 2: structured school year / grade / section management ----

data class AcademicYear(
    val id: String = "",
    val name: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val isCurrent: Boolean = false,
    val status: String = "active"
)

data class GradeSection(
    val id: String = "",
    val academicYearId: String = "",
    val academicYearName: String = "",
    val gradeLevel: String = "",
    val sectionName: String = "",
    val active: Boolean = true
) {
    val displayName: String get() = "Grade $gradeLevel · $sectionName"
}

data class AcademicStructureResponse(
    val currentAcademicYear: AcademicYear? = null,
    val academicYears: List<AcademicYear> = emptyList(),
    val gradeSections: List<GradeSection> = emptyList()
)

data class CreateAcademicYearRequest(
    val name: String,
    val startDate: String = "",
    val endDate: String = "",
    val current: Boolean = false
)

data class CreateGradeSectionRequest(
    val academicYearId: String,
    val gradeLevel: String,
    val sectionName: String
)

data class GradeSectionStatusRequest(val active: Boolean)
// ---- Phase 2: production bulk student import ----

data class BulkImportError(
    val row: Int = 0,
    val field: String = "",
    val message: String = ""
)

data class BulkImportSampleStudent(
    val studentNumber: String = "",
    val fullName: String = "",
    val grade: String = "",
    val section: String = ""
)

data class BulkStudentImportResponse(
    val dryRun: Boolean = true,
    val totalRows: Int = 0,
    val validRows: Int = 0,
    val invalidRows: Int = 0,
    val duplicateRows: Int = 0,
    val importedRows: Int = 0,
    val readyToImport: Boolean = false,
    val errors: List<BulkImportError> = emptyList(),
    val sample: List<BulkImportSampleStudent> = emptyList(),
    val error: String? = null
)



// ---- Phase 2: student lifecycle + end-of-year promotion ----

data class StudentLifecycleItem(
    val studentId: String = "",
    val fullName: String = "",
    val grade: String = "",
    val section: String = "",
    val status: String = "active",
    val academicYearId: String = "",
    val academicYearName: String = "",
    val gradeSectionId: String = "",
    val studentNumber: String = ""
)

data class StudentLifecycleResponse(
    val students: List<StudentLifecycleItem> = emptyList(),
    val counts: Map<String, Int> = emptyMap()
)

data class StudentStatusRequest(
    val status: String,
    val reason: String = ""
)

data class PromotionRequest(
    val targetAcademicYearId: String,
    val dryRun: Boolean = true,
    val sectionMappings: Map<String, String> = emptyMap(),
    val graduateStudentIds: List<String> = emptyList()
)

data class PromotionStudentPreview(
    val studentId: String = "",
    val fullName: String = "",
    val grade: String = "",
    val section: String = "",
    val targetGrade: String = "",
    val targetSection: String = "",
    val reason: String = ""
)

data class PromotionResponse(
    val dryRun: Boolean = true,
    val targetAcademicYearId: String = "",
    val targetAcademicYearName: String = "",
    val readyCount: Int = 0,
    val unresolvedCount: Int = 0,
    val graduatingCount: Int = 0,
    val promotedCount: Int = 0,
    val graduatedCount: Int = 0,
    val readySample: List<PromotionStudentPreview> = emptyList(),
    val unresolved: List<PromotionStudentPreview> = emptyList(),
    val error: String? = null
)

// ---- Phase 2: dismissal reporting / export ----

data class DismissalReportSummary(
    val from: String = "",
    val to: String = "",
    val timeZone: String = "Asia/Manila",
    val grade: String? = null,
    val section: String? = null,
    val totalReleases: Int = 0,
    val uniqueStudentsReleased: Int = 0,
    val qrReleases: Int = 0,
    val manualOverrides: Int = 0,
    val dailyCounts: Map<String, Int> = emptyMap(),
    val gradeSectionCounts: Map<String, Int> = emptyMap()
)
