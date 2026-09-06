package com.pickuppass.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicPlacementRulesTest {

    @Test
    fun currentPlacementOptions_onlyReturnsActiveSectionsFromCurrentYear() {
        val structure = AcademicStructureResponse(
            currentAcademicYear = AcademicYear(
                id = "ay-current",
                name = "2026-2027",
                isCurrent = true
            ),
            academicYears = listOf(
                AcademicYear(id = "ay-current", name = "2026-2027", isCurrent = true),
                AcademicYear(id = "ay-old", name = "2025-2026")
            ),
            gradeSections = listOf(
                GradeSection(
                    id = "gs-current",
                    academicYearId = "ay-current",
                    gradeLevel = "6",
                    sectionName = "Rizal",
                    active = true
                ),
                GradeSection(
                    id = "gs-inactive",
                    academicYearId = "ay-current",
                    gradeLevel = "6",
                    sectionName = "Bonifacio",
                    active = false
                ),
                GradeSection(
                    id = "gs-old",
                    academicYearId = "ay-old",
                    gradeLevel = "5",
                    sectionName = "Mabini",
                    active = true
                )
            )
        )

        val result = structure.currentPlacementOptions()

        assertEquals(1, result.size)
        assertEquals("gs-current", result.single().gradeSectionId)
        assertEquals("6", result.single().grade)
        assertEquals("Rizal", result.single().section)
        assertEquals("2026-2027", result.single().academicYearName)
    }

    @Test
    fun currentPlacementOptionsFor_intersectsCurrentSetupWithTeacherAssignments() {
        val structure = AcademicStructureResponse(
            currentAcademicYear = AcademicYear(
                id = "ay-current",
                name = "2026-2027",
                isCurrent = true
            ),
            gradeSections = listOf(
                GradeSection(
                    id = "gs-6-rizal",
                    academicYearId = "ay-current",
                    gradeLevel = "6",
                    sectionName = "Rizal"
                ),
                GradeSection(
                    id = "gs-6-bonifacio",
                    academicYearId = "ay-current",
                    gradeLevel = "6",
                    sectionName = "Bonifacio"
                )
            )
        )

        val result = structure.currentPlacementOptionsFor(
            listOf(
                TeacherSection("6", "rizal"),
                TeacherSection("5", "Legacy")
            )
        )

        assertEquals(1, result.size)
        assertEquals("gs-6-rizal", result.single().gradeSectionId)
    }

    @Test
    fun currentPlacementOptions_returnsEmptyWithoutCurrentAcademicYear() {
        val structure = AcademicStructureResponse(
            currentAcademicYear = null,
            gradeSections = listOf(
                GradeSection(
                    id = "gs-1",
                    academicYearId = "ay-1",
                    gradeLevel = "1",
                    sectionName = "A"
                )
            )
        )

        assertTrue(structure.currentPlacementOptions().isEmpty())
    }
}
