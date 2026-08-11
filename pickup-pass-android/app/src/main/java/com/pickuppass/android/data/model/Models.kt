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

/** Firestore: schools/{schoolId} */
data class SchoolInfo(
    var id: String = "",
    val schoolName: String = "",
    val status: String = "",
    val logoUrl: String? = null,
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
