package com.pickuppass.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pickuppass.android.data.model.CreateStudentRequest
import com.pickuppass.android.data.model.RegisterParentRequest
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.remote.PickupPassApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeacherRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val api: PickupPassApi
) {
    /**
     * Full roster for a school, shared by teacher and school_admin — same
     * query shape as the web app's teacher/students.html, backed by the
     * same schoolId+fullName composite index (already required for that
     * page, so no new Firestore index needed for Android either).
     */
    suspend fun getSchoolStudents(schoolId: String): Result<List<Student>> = runCatching {
        val snapshot = firestore.collection("students")
            .whereEqualTo("schoolId", schoolId)
            .orderBy("fullName", Query.Direction.ASCENDING)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(Student::class.java)?.also { it.id = doc.id }
        }.filter { it.status.isBlank() || it.status.equals("active", ignoreCase = true) }
    }

    /**
     * A teacher's own assigned sections, read directly from their
     * users/{uid} Firestore doc — the same field BroadcastService reads
     * server-side to scope "announce to my section." No dedicated backend
     * endpoint for this (unlike the school-admin's staff list, which needs
     * one to see OTHER people's data) — reading your own profile doc is
     * already permitted by the existing security rules
     * (request.auth.uid == userId), so a direct Firestore read is the
     * simplest option consistent with how the rest of the app works.
     */
    suspend fun getMyAssignedSections(uid: String): Result<List<com.pickuppass.android.data.model.TeacherSection>> = runCatching {
        val doc = firestore.collection("users").document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val raw = doc.get("assignedSections") as? List<Map<String, String>> ?: emptyList()
        raw.mapNotNull { entry ->
            val grade = entry["grade"]
            val section = entry["section"]
            if (grade != null && section != null) com.pickuppass.android.data.model.TeacherSection(grade, section) else null
        }
    }

    /**
     * Roster scoped to only the given grade/section pairs — what a
     * teacher's student list actually queries, as opposed to
     * getSchoolStudents() above (school_admin's unrestricted view).
     *
     * Firestore has no clean "match any of these (grade, section) pairs"
     * in one query without a computed composite field, and a computed
     * field would mean every existing student doc needs a one-time
     * migration to backfill it. Since a teacher's assignedSections list is
     * always small (a handful of sections at most), running one query per
     * pair and merging in memory is simpler and avoids that migration
     * entirely — same reasoning already used for BroadcastService's
     * section-targeting on the backend.
     */
    suspend fun getStudentsForSections(
        schoolId: String,
        sections: List<com.pickuppass.android.data.model.TeacherSection>
    ): Result<List<Student>> = runCatching {
        if (sections.isEmpty()) return@runCatching emptyList()

        val results = mutableListOf<Student>()
        val seenIds = mutableSetOf<String>() // a student's grade+section is unique, but guard against duplicates defensively
        for (s in sections) {
            val snapshot = firestore.collection("students")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("grade", s.grade)
                .whereEqualTo("section", s.section)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                if (seenIds.add(doc.id)) {
                    doc.toObject(Student::class.java)?.also { it.id = doc.id }?.let {
                        if (it.status.isBlank() || it.status.equals("active", ignoreCase = true)) results.add(it)
                    }
                }
            }
        }
        results.sortedBy { it.fullName }
    }

    suspend fun createStudent(
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        grade: String,
        section: String
    ): ApiResult<String> {
        return try {
            val response = api.createStudent(
                CreateStudentRequest(
                    lastName = lastName,
                    firstName = firstName,
                    middleInitial = middleInitial.ifBlank { null },
                    suffix = suffix.ifBlank { null },
                    grade = grade,
                    section = section
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.studentId != null) {
                ApiResult.Success(body.studentId)
            } else {
                ApiResult.Failure(body?.error ?: "Could not add student")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun registerParent(
        studentId: String,
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        parentEmail: String,
        relationship: String
    ): ApiResult<com.pickuppass.android.data.model.AddGuardianResponse> {
        return try {
            val response = api.registerParent(
                RegisterParentRequest(
                    studentId = studentId,
                    parentEmail = parentEmail,
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
                ApiResult.Failure(body?.error ?: "Could not register parent")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /**
     * Dismissal history, enriched with student/guardian/staff display info.
     * Firestore has no joins — exitLogs only stores studentId/parentUid/
     * verifiedByUid — so this fetches the school's roster and every
     * distinct user referenced by the log batch ONCE and joins client-side,
     * rather than one read per log (which would multiply Firestore reads
     * well past what's reasonable on the free plan's daily quota).
     * Bounded to the 200 most recent entries for the same reason.
     */
    suspend fun getExitLogs(schoolId: String): Result<List<com.pickuppass.android.data.model.ExitLogEntry>> = runCatching {
        val logsSnapshot = firestore.collection("exitLogs")
            .whereEqualTo("schoolId", schoolId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .get()
            .await()

        val studentsSnapshot = firestore.collection("students")
            .whereEqualTo("schoolId", schoolId)
            .get()
            .await()

        val studentsById = studentsSnapshot.documents.associateBy(
            { it.id },
            { it.data ?: emptyMap<String, Any>() }
        )

        val uidsNeeded = mutableSetOf<String>()
        logsSnapshot.documents.forEach { doc ->
            (doc.getString("parentUid"))?.let { uidsNeeded.add(it) }
            (doc.getString("verifiedByUid"))?.let { uidsNeeded.add(it) }
        }

        val usersById = mutableMapOf<String, Map<String, Any>>()
        // Small, deduplicated set (a couple of people per log) — fetched
        // individually since the modular-equivalent Firestore Android SDK
        // has no batch-get-by-ids call without an extra composite setup.
        for (uid in uidsNeeded) {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                usersById[uid] = userDoc.data ?: emptyMap()
            }
        }

        logsSnapshot.documents.map { doc ->
            val studentId = doc.getString("studentId") ?: ""
            val student = studentsById[studentId] ?: emptyMap()
            val guardian = usersById[doc.getString("parentUid")] ?: emptyMap()
            val staff = usersById[doc.getString("verifiedByUid")] ?: emptyMap()

            com.pickuppass.android.data.model.ExitLogEntry(
                id = doc.id,
                studentName = student["fullName"] as? String ?: "Unknown Student",
                grade = student["grade"] as? String ?: "",
                section = student["section"] as? String ?: "",
                guardianName = guardian["displayName"] as? String ?: "Unknown Guardian",
                staffName = staff["displayName"] as? String ?: "Unknown Staff",
                timestampMillis = doc.getTimestamp("timestamp")?.toDate()?.time,
            )
        }
    }

    suspend fun broadcastToSection(title: String, body: String): ApiResult<Int> {
        return try {
            val response = api.broadcastToSection(
                com.pickuppass.android.data.model.BroadcastRequest(title = title, body = body)
            )
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
