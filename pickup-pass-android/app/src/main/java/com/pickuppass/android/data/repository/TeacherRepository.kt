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
        }
    }

    suspend fun createStudent(fullName: String, grade: String, section: String): ApiResult<String> {
        return try {
            val response = api.createStudent(CreateStudentRequest(fullName, grade, section))
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
        parentName: String,
        parentEmail: String,
        relationship: String
    ): ApiResult<com.pickuppass.android.data.model.AddGuardianResponse> {
        return try {
            val response = api.registerParent(
                RegisterParentRequest(studentId, parentEmail, parentName, relationship)
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
}
