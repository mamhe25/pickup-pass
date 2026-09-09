package com.pickuppass.android.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pickuppass.android.data.model.PickupPolicyInfo
import com.pickuppass.android.data.model.SchoolInfo
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
        doc.toSchoolInfo()
    }

    /**
     * Live school metadata for operational screens whose behavior changes
     * immediately when the platform updates the tenant (for example launch
     * approval switching dismissal from pre-launch test to production).
     */
    fun observeSchool(schoolId: String): Flow<SchoolInfo?> =
        callbackFlow {
            val registration =
                firestore.collection("schools")
                    .document(schoolId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        trySend(snapshot?.toSchoolInfo())
                    }

            awaitClose {
                registration.remove()
            }
        }

    private fun DocumentSnapshot.toSchoolInfo(): SchoolInfo? {
        if (!exists()) return null

        val pickupPolicy =
            (get("pickupPolicy") as? Map<*, *>)?.let { policy ->
                PickupPolicyInfo(
                    mode = policy["mode"] as? String ?: "unrestricted",
                    earliestPickupTime =
                        policy["earliestPickupTime"] as? String ?: "",
                    latestPickupTime =
                        policy["latestPickupTime"] as? String ?: "",
                    allowManualOverride =
                        policy["allowManualOverride"] as? Boolean ?: true
                )
            }

        return SchoolInfo(
            id = id,
            schoolName = getString("schoolName").orEmpty(),
            status = getString("status").orEmpty(),
            launchStatus =
                getString("launchStatus")
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() }
                    ?: "draft",
            logoUrl = getString("logoUrl"),
            pickupPolicy = pickupPolicy
        )
    }
}
