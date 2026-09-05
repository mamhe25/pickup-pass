package com.pickuppass.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.pickuppass.android.data.model.PickupPolicyInfo
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /** All students the given uid is an authorized guardian for, within one school. */
    suspend fun getMyStudents(uid: String, schoolId: String): Result<List<Student>> = runCatching {
        val snapshot = firestore.collection("students")
            .whereEqualTo("schoolId", schoolId)
            .whereArrayContains("guardianUids", uid)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(Student::class.java)?.also { it.id = doc.id }
        }.filter { student ->
            val activeStudent = student.status.isBlank() || student.status.equals("active", ignoreCase = true)
            val entry = student.guardians[uid]
            val guardianCurrentlyAuthorized = entry == null || !entry.authorizationType.equals("temporary", ignoreCase = true) ||
                (entry.validDate == LocalDate.now().toString() && entry.remainingUses > 0)
            activeStudent && guardianCurrentlyAuthorized
        }
    }

    suspend fun getStudent(studentId: String): Result<Student?> = runCatching {
        val doc = firestore.collection("students").document(studentId).get().await()
        doc.toObject(Student::class.java)?.also { it.id = doc.id }
    }

    suspend fun getUserProfile(uid: String): Result<UserProfile?> = runCatching {
        val doc = firestore.collection("users").document(uid).get().await()
        doc.toObject(UserProfile::class.java)?.also { it.uid = doc.id }
    }

    /**
     * Used to show which school a signed-in parent/staff member belongs to.
     *
     * School documents contain backend-managed metadata (for example
     * createdAt/logoUpdatedAt) that Android does not need. Read only the
     * fields consumed by the app instead of mapping the whole Firestore
     * document into SchoolInfo, which avoids CustomClassMapper warnings and
     * keeps the Android model intentionally decoupled from server metadata.
     */
    suspend fun getSchool(schoolId: String): Result<SchoolInfo?> = runCatching {
        val doc = firestore.collection("schools").document(schoolId).get().await()
        if (!doc.exists()) {
            return@runCatching null
        }

        val pickupPolicy = (doc.get("pickupPolicy") as? Map<*, *>)?.let { policy ->
            PickupPolicyInfo(
                mode = policy["mode"] as? String ?: "unrestricted",
                earliestPickupTime = policy["earliestPickupTime"] as? String ?: "",
                latestPickupTime = policy["latestPickupTime"] as? String ?: "",
                allowManualOverride = policy["allowManualOverride"] as? Boolean ?: true
            )
        }

        SchoolInfo(
            id = doc.id,
            schoolName = doc.getString("schoolName").orEmpty(),
            status = doc.getString("status").orEmpty(),
            logoUrl = doc.getString("logoUrl"),
            pickupPolicy = pickupPolicy
        )
    }
}
