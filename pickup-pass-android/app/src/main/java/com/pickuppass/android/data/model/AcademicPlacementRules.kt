package com.pickuppass.android.data.model

data class AcademicPlacementOption(
    val gradeSectionId: String,
    val academicYearId: String,
    val academicYearName: String,
    val grade: String,
    val section: String
) {
    val displayName: String
        get() {
            val gradeLabel = grade
                .ifBlank { "—" }
                .let {
                    if (it.startsWith("Grade", ignoreCase = true)) {
                        it
                    } else {
                        "Grade " + it
                    }
                }
            return gradeLabel + " · " + section.ifBlank { "Section —" }
        }
}

fun AcademicStructureResponse.currentPlacementOptions(): List<AcademicPlacementOption> {
    val currentYear = currentAcademicYear ?: return emptyList()

    return gradeSections
        .asSequence()
        .filter { it.active && it.academicYearId == currentYear.id }
        .map {
            AcademicPlacementOption(
                gradeSectionId = it.id,
                academicYearId = it.academicYearId,
                academicYearName = currentYear.name.ifBlank { it.academicYearName },
                grade = it.gradeLevel.trim(),
                section = it.sectionName.trim()
            )
        }
        .filter {
            it.gradeSectionId.isNotBlank() &&
                it.academicYearId.isNotBlank() &&
                it.grade.isNotBlank() &&
                it.section.isNotBlank()
        }
        .distinctBy { it.placementKey() }
        .sortedWith(
            compareBy<AcademicPlacementOption>(
                { gradeSortKey(it.grade) },
                { it.section.lowercase() }
            )
        )
        .toList()
}

fun AcademicStructureResponse.currentPlacementOptionsFor(
    assignments: List<TeacherSection>
): List<AcademicPlacementOption> {
    val keys = assignments
        .map { placementKey(it.grade, it.section) }
        .toSet()

    if (keys.isEmpty()) return emptyList()

    return currentPlacementOptions()
        .filter { it.placementKey() in keys }
}

fun AcademicPlacementOption.matches(section: TeacherSection): Boolean =
    placementKey() == placementKey(section.grade, section.section)

fun AcademicPlacementOption.placementKey(): String =
    placementKey(grade, section)

fun placementKey(grade: String, section: String): String =
    "${grade.trim().lowercase()}||${section.trim().lowercase()}"

private fun gradeSortKey(grade: String): String {
    val numeric = Regex("\\d+")
        .find(grade)
        ?.value
        ?.toIntOrNull()

    return if (numeric != null) {
        "%05d".format(numeric)
    } else {
        "99999-${grade.lowercase()}"
    }
}
