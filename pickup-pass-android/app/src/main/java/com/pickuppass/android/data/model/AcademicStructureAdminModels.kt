package com.pickuppass.android.data.model

/**
 * Mutation payloads for production academic-structure administration.
 *
 * Kept separate from the read models so adding/editing/deleting configuration
 * does not expand the already-large shared Models.kt file.
 */
data class UpdateAcademicYearRequest(
    val name: String,
    val startDate: String = "",
    val endDate: String = ""
)

data class AcademicYearStatusRequest(
    val active: Boolean
)

data class UpdateGradeSectionRequest(
    val gradeLevel: String,
    val sectionName: String
)
