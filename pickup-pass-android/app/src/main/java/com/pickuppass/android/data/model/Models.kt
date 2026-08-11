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
    val authorizationType: String = "permanent",
    val validDate: String = "",
    val remainingUses: Int = 0,
    val pickupScheduleEnabled: Boolean = false,
    val pickupDays: List<String> = emptyList(),
    val scheduleStartDate: String = "",
    val scheduleEndDate: String = "",
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

data class AddTemporaryGuardianRequest(
    val studentId: String,
    val guardianEmail: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null,
    val relationship: String,
    val validDate: String
)

data class GuardianScheduleRequest(
    val studentId: String,
    val guardianUid: String,
    val enabled: Boolean,
    val pickupDays: List<String> = emptyList(),
    val startDate: String = "",
    val endDate: String = ""
)

data class GuardianScheduleResponse(
    val status: String? = null,
    val enabled: Boolean = false,
    val pickupDays: List<String> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val invalidatedQrPasses: Int = 0,
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
    val pickupGateId: String = "",
    val pickupGateName: String = "",
    val campusId: String = "",
    val campusName: String = "",
    val timestamp: String? = null
)

data class GateActivityItem(
    val pickupGateId: String = "",
    val pickupGateName: String = "",
    val campusId: String = "",
    val campusName: String = "",
    val releaseCount: Int = 0,
    val qrReleaseCount: Int = 0,
    val manualOverrideCount: Int = 0
)

data class CampusActivityItem(
    val campusId: String = "",
    val campusName: String = "",
    val releaseCount: Int = 0
)

data class DismissalDashboardResponse(
    val businessDate: String = "",
    val timeZone: String = "Asia/Manila",
    val totalStudents: Int = 0,
    val releasedCount: Int = 0,
    val remainingCount: Int = 0,
    val releaseRatePercent: Double = 0.0,
    val qrReleaseCount: Int = 0,
    val manualOverrideCount: Int = 0,
    val gateActivity: List<GateActivityItem> = emptyList(),
    val campusActivity: List<CampusActivityItem> = emptyList(),
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


// ---- Phase 2: guardian identity verification ----

data class GuardianVerificationItem(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val status: String = "verified",
    val studentNames: List<String> = emptyList(),
    val verificationReason: String = "",
    val verifiedAt: String? = null
)

data class GuardianVerificationResponse(
    val verificationRequired: Boolean = false,
    val guardians: List<GuardianVerificationItem> = emptyList()
)

data class GuardianVerificationPolicyRequest(val required: Boolean)

data class GuardianVerificationStatusRequest(
    val status: String,
    val reason: String = ""
)

data class GuardianVerificationStatusResponse(
    val status: String = "",
    val invalidatedTokens: Int = 0
)


// ---- Phase 2: device / session management ----

data class DeviceSessionItem(
    val deviceId: String = "",
    val deviceName: String = "",
    val clientVersion: String = "",
    val createdAt: String? = null,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val current: Boolean = false,
    val active: Boolean = true
)

data class DeviceSessionsResponse(
    val devices: List<DeviceSessionItem> = emptyList()
)

data class DeviceRevokeResponse(
    val status: String = "",
    val revokedCount: Int = 0
)

// Phase 2: scheduled school announcements and delivery history.
data class ScheduleBroadcastRequest(
    val title: String,
    val body: String,
    val audience: List<String>,
    val scheduledAt: String
)

data class ScheduleBroadcastResponse(
    val broadcastId: String = "",
    val status: String = "",
    val scheduledAt: String? = null,
    val error: String? = null
)

data class BroadcastHistoryItem(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "",
    val deliveryMode: String = "",
    val audience: List<String> = emptyList(),
    val recipientCount: Int = 0,
    val createdAt: String? = null,
    val scheduledAt: String? = null,
    val sentAt: String? = null,
    val cancelledAt: String? = null,
    val errorMessage: String? = null
)

data class BroadcastHistoryResponse(
    val broadcasts: List<BroadcastHistoryItem> = emptyList()
)


// ---- Phase 2: campus and pickup gate management ----
data class CampusItem(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val active: Boolean = true
)

data class PickupGateItem(
    val id: String = "",
    val campusId: String = "",
    val campusName: String = "",
    val name: String = "",
    val description: String = "",
    val active: Boolean = true
) {
    val displayName: String get() = if (campusName.isBlank()) name else "$campusName · $name"
}

data class ActivePickupGatesResponse(
    val gates: List<PickupGateItem> = emptyList()
)

data class CampusGateResponse(
    val campuses: List<CampusItem> = emptyList(),
    val gates: List<PickupGateItem> = emptyList()
)

data class CreateCampusRequest(val name: String, val address: String = "")
data class CreatePickupGateRequest(val campusId: String = "", val name: String, val description: String = "")
data class ActiveStatusRequest(val active: Boolean)

// ---- Phase 2: staff pickup gate assignments ----
data class StaffPickupGateAssignment(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "teacher",
    val isActive: Boolean = true,
    val assignedPickupGateIds: List<String> = emptyList(),
    val allGates: Boolean = true
)

data class StaffPickupGateResponse(
    val staff: List<StaffPickupGateAssignment> = emptyList(),
    val gates: List<PickupGateItem> = emptyList()
)

data class UpdateStaffPickupGatesRequest(val pickupGateIds: List<String>)


// ---- Phase 3: master-admin SaaS tenant console ----
data class TenantUsageSummary(
    val activeStudents: Long = 0,
    val activeStaff: Long = 0,
    val activeCampuses: Long = 0,
    val totalQrPickups: Long = 0,
    val totalManualPickups: Long = 0,
    val studentLimit: Long = -1,
    val staffLimit: Long = -1,
    val campusLimit: Long = -1,
    val studentsOverLimit: Boolean = false,
    val staffOverLimit: Boolean = false,
    val campusesOverLimit: Boolean = false
)

data class MasterSchoolItem(
    val schoolId: String = "",
    val schoolName: String = "",
    val status: String = "active",
    val plan: String = "trial",
    val subscriptionStatus: String = "trialing",
    val subscriptionAccessActive: Boolean = true,
    val trialEndsAt: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val graceEndsAt: String? = null,
    val autoRenew: Boolean = true,
    val cancelAtPeriodEnd: Boolean = false,
    val features: Map<String, Boolean> = emptyMap(),
    val featureOverrides: Map<String, Boolean> = emptyMap(),
    val limits: Map<String, Int> = emptyMap(),
    val usage: TenantUsageSummary = TenantUsageSummary(),
    val createdAt: String? = null,
    val statusUpdatedAt: String? = null
)

data class MasterSchoolListResponse(
    val totalSchools: Int = 0,
    val activeSchools: Int = 0,
    val suspendedSchools: Int = 0,
    val schools: List<MasterSchoolItem> = emptyList()
)

data class CreateMasterSchoolRequest(val schoolName: String)

data class CreateMasterSchoolResponse(
    val schoolId: String = "",
    val schoolName: String = ""
)

data class CreateMasterStaffRequest(
    val email: String,
    val lastName: String,
    val firstName: String,
    val middleInitial: String? = null,
    val suffix: String? = null,
    val role: String = "school_admin"
)

data class CreateMasterStaffResponse(
    val uid: String = "",
    val role: String = "",
    val schoolId: String = "",
    val emailSent: Boolean = false
)

data class MasterPlanDefinition(
    val displayName: String = "",
    val maxStudents: Int = -1,
    val maxStaff: Int = -1,
    val maxCampuses: Int = -1,
    val features: Map<String, Boolean> = emptyMap()
)

data class MasterPlanCatalogResponse(
    val plans: Map<String, MasterPlanDefinition> = emptyMap(),
    val featureKeys: List<String> = emptyList()
)

data class UpdateMasterSubscriptionRequest(
    val plan: String,
    val subscriptionStatus: String,
    val trialEndsAt: String? = null,
    val featureOverrides: Map<String, Boolean> = emptyMap(),
    val autoRenew: Boolean = true,
    val cancelAtPeriodEnd: Boolean = false,
    val startNewPeriod: Boolean = false,
    val extendTrialDays: Int = 0
)

data class MasterSubscriptionResponse(
    val schoolId: String = "",
    val plan: String = "",
    val subscriptionStatus: String = "",
    val subscriptionAccessActive: Boolean = true,
    val trialEndsAt: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val graceEndsAt: String? = null,
    val autoRenew: Boolean = true,
    val cancelAtPeriodEnd: Boolean = false,
    val features: Map<String, Boolean> = emptyMap(),
    val featureOverrides: Map<String, Boolean> = emptyMap(),
    val limits: Map<String, Int> = emptyMap()
)




// ---- Phase 3: master-admin operations health dashboard ----
data class MasterOperationsMetrics(
    val totalSchools: Int = 0,
    val healthySchools: Int = 0,
    val attentionNeededSchools: Int = 0,
    val billingRiskSchools: Int = 0,
    val overQuotaSchools: Int = 0,
    val suspendedSchools: Int = 0,
    val pendingGcashReviews: Int = 0,
    val overdueInvoices: Int = 0,
    val expiringSubscriptions: Int = 0,
    val quotaWarnings: Int = 0,
    val billingEmailFailures: Int = 0
)

data class MasterTenantHealthItem(
    val schoolId: String = "",
    val schoolName: String = "",
    val status: String = "active",
    val plan: String = "trial",
    val subscriptionStatus: String = "trialing",
    val healthState: String = "healthy",
    val activeAlertCount: Int = 0,
    val criticalAlertCount: Int = 0,
    val warningAlertCount: Int = 0,
    val maxQuotaPercent: Long = 0
)

data class MasterOperationalAlert(
    val alertId: String = "",
    val schoolId: String = "",
    val schoolNameSnapshot: String = "",
    val type: String = "",
    val severity: String = "warning",
    val healthImpact: String = "attention_needed",
    val title: String = "",
    val message: String = "",
    val action: String = "",
    val resourceId: String = "",
    val firstSeenAt: String? = null,
    val lastSeenAt: String? = null
)

data class MasterOperationsOverviewResponse(
    val generatedAt: String? = null,
    val lastScanAt: String? = null,
    val metrics: MasterOperationsMetrics = MasterOperationsMetrics(),
    val tenants: List<MasterTenantHealthItem> = emptyList(),
    val alerts: List<MasterOperationalAlert> = emptyList()
)

data class MasterOperationsRefreshResponse(
    val executed: Boolean = false,
    val activeAlerts: Int = 0,
    val resolvedAlerts: Int = 0,
    val status: String = ""
)




// ---- Phase 3: master-admin security center ----
data class MasterSecurityMetrics(
    val activeAlerts: Int = 0,
    val openAlerts: Int = 0,
    val acknowledged: Int = 0,
    val critical: Int = 0,
    val high: Int = 0,
    val medium: Int = 0
)

data class MasterSecurityAlert(
    val id: String = "",
    val type: String = "",
    val severity: String = "medium",
    val schoolId: String? = null,
    val uid: String? = null,
    val role: String? = null,
    val title: String = "",
    val message: String = "",
    val action: String = "security",
    val status: String = "open",
    val occurrences: Long = 1,
    val firstSeenAt: String? = null,
    val lastSeenAt: String? = null,
    val statusUpdatedAt: String? = null
)

data class MasterPrivilegedAuditEvent(
    val id: String = "",
    val schoolId: String? = null,
    val actorUid: String = "",
    val actorRole: String = "",
    val action: String = "",
    val resourceType: String = "",
    val resourceId: String = "",
    val timestamp: String? = null
)

data class MasterSecurityOverviewResponse(
    val generatedAt: String? = null,
    val metrics: MasterSecurityMetrics = MasterSecurityMetrics(),
    val alerts: List<MasterSecurityAlert> = emptyList(),
    val recentPrivilegedActions: List<MasterPrivilegedAuditEvent> = emptyList()
)

data class MasterSecurityAlertStatusRequest(
    val status: String,
    val note: String? = null
)

data class MasterSecurityRevokeSessionsRequest(val reason: String)

// ---- Phase 3: tenant subscription entitlements ----
data class TenantEntitlementsResponse(
    val plan: String = "trial",
    val subscriptionStatus: String = "trialing",
    val subscriptionAccessActive: Boolean = true,
    val trialEndsAt: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val graceEndsAt: String? = null,
    val autoRenew: Boolean = true,
    val cancelAtPeriodEnd: Boolean = false,
    val features: Map<String, Boolean> = emptyMap(),
    val limits: Map<String, Int> = emptyMap()
)


// ---- Phase 3: master-admin billing ledger ----
data class MasterInvoiceItem(
    val invoiceId: String = "",
    val schoolId: String = "",
    val schoolNameSnapshot: String = "",
    val billingNameSnapshot: String = "",
    val billingEmailSnapshot: String = "",
    val billingAddressSnapshot: String = "",
    val billingTaxIdSnapshot: String = "",
    val invoiceNumber: String = "",
    val planSnapshot: String = "",
    val amountMinor: Long = 0,
    val currency: String = "PHP",
    val status: String = "open",
    val dueAt: String? = null,
    val note: String = "",
    val paymentReference: String = "",
    val paymentMethod: String = "",
    val paymentNote: String = "",
    val paymentProvider: String = "",
    val providerEventId: String = "",
    val voidReason: String = "",
    val lastEmailedTo: String = "",
    val emailDeliveryCount: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val paidAt: String? = null,
    val lastEmailedAt: String? = null
)

data class MasterInvoiceListResponse(val invoices: List<MasterInvoiceItem> = emptyList())
data class CreateMasterInvoiceRequest(
    val amountMinor: Long,
    val currency: String = "PHP",
    val dueAt: String? = null,
    val note: String? = null
)
data class MarkMasterInvoicePaidRequest(
    val paymentReference: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null
)
data class VoidMasterInvoiceRequest(val reason: String? = null)


data class MasterBillingProfileResponse(
    val schoolId: String = "",
    val billingName: String = "",
    val billingEmail: String = "",
    val billingAddress: String = "",
    val billingTaxId: String = ""
)

data class UpdateMasterBillingProfileRequest(
    val billingName: String,
    val billingEmail: String,
    val billingAddress: String,
    val billingTaxId: String
)

data class EmailMasterInvoiceRequest(val recipientEmail: String? = null)
data class EmailMasterInvoiceResponse(
    val status: String = "",
    val recipient: String = "",
    val invoiceNumber: String = ""
)


// ---- Phase 3: startup manual GCash billing ----
data class SchoolBillingPaymentInstructions(
    val enabled: Boolean = false,
    val accountName: String = "",
    val mobile: String = "",
    val note: String = "",
    val verificationMode: String = "manual_review"
)

data class SchoolBillingInvoiceItem(
    val invoiceId: String = "",
    val invoiceNumber: String = "",
    val planSnapshot: String = "",
    val amountMinor: Long = 0,
    val currency: String = "PHP",
    val status: String = "open",
    val dueAt: String? = null,
    val note: String = "",
    val paymentReference: String = "",
    val paymentMethod: String = "",
    val createdAt: String? = null,
    val paidAt: String? = null,
    val receiptAvailable: Boolean = false,
    val receiptNumber: String = ""
)

data class GcashPaymentNoticeItem(
    val noticeId: String = "",
    val schoolId: String = "",
    val invoiceId: String = "",
    val invoiceNumber: String = "",
    val amountMinor: Long = 0,
    val currency: String = "PHP",
    val payerName: String = "",
    val referenceNumber: String = "",
    val note: String = "",
    val status: String = "pending_review",
    val reviewNote: String = "",
    val paidAtClaimed: String? = null,
    val createdAt: String? = null,
    val reviewedAt: String? = null
)

data class SchoolBillingCenterResponse(
    val paymentInstructions: SchoolBillingPaymentInstructions = SchoolBillingPaymentInstructions(),
    val invoices: List<SchoolBillingInvoiceItem> = emptyList(),
    val paymentNotices: List<GcashPaymentNoticeItem> = emptyList()
)

data class SubmitGcashPaymentNoticeRequest(
    val payerName: String,
    val referenceNumber: String,
    val amountMinor: Long? = null,
    val paidAt: String? = null,
    val note: String? = null
)

data class GcashPaymentNoticeListResponse(val paymentNotices: List<GcashPaymentNoticeItem> = emptyList())
data class GcashPaymentNoticeReviewRequest(val note: String? = null)
