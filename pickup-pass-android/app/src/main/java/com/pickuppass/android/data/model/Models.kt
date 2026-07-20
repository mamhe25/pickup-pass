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
    val guardianName: String,
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
    val parentName: String,
    val relationship: String? = null
)

data class CreateStudentRequest(
    val fullName: String,
    val grade: String,
    val section: String
)

data class CreateStudentResponse(
    val studentId: String? = null,
    val fullName: String? = null,
    val error: String? = null
)

data class InviteTeacherRequest(val email: String, val displayName: String)

data class InviteTeacherResponse(
    val uid: String? = null,
    val role: String? = null,
    val emailSent: Boolean = true,
    val error: String? = null
)

data class DeviceTokenRequest(val token: String)

data class LogoUploadResponse(val logoUrl: String? = null, val error: String? = null)
