package com.pickuppass.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardianRelationshipTest {

    @Test
    fun explicitPrimaryWinsEvenWhenItIsNotFirst() {
        val student =
            Student(
                guardianUids = listOf("backup", "primary"),
                guardians =
                    linkedMapOf(
                        "backup" to GuardianEntry(isPrimary = false),
                        "primary" to GuardianEntry(isPrimary = true)
                    )
            )

        assertEquals("primary", student.primaryGuardianUidCompat())
    }

    @Test
    fun guardianUidsOnlyLegacyRecordPreservesPrimary() {
        val student =
            Student(
                guardianUids = listOf("legacy-primary"),
                guardians = emptyMap()
            )

        assertEquals("legacy-primary", student.primaryGuardianUidCompat())
    }

    @Test
    fun nullIsPrimaryLegacyEntryPreservesPrimary() {
        val student =
            Student(
                guardianUids = listOf("legacy-primary"),
                guardians =
                    mapOf(
                        "legacy-primary" to GuardianEntry(isPrimary = null)
                    )
            )

        assertEquals("legacy-primary", student.primaryGuardianUidCompat())
    }

    @Test
    fun explicitFalseIsNotPromoted() {
        val student =
            Student(
                guardianUids = listOf("backup-only"),
                guardians =
                    mapOf(
                        "backup-only" to GuardianEntry(isPrimary = false)
                    )
            )

        assertNull(student.primaryGuardianUidCompat())
    }

    @Test
    fun emptyLinkedListHasNoPrimary() {
        val student =
            Student(
                guardianUids = emptyList(),
                guardians =
                    mapOf(
                        "orphan" to GuardianEntry(isPrimary = true)
                    )
            )

        assertNull(student.primaryGuardianUidCompat())
    }
}
