# Phase 2 Update 4 — Student Lifecycle & Promotion

This cumulative update adds production-safe student lifecycle management without deleting historical student records.

## Student statuses

- `active` — normal roster and pickup access
- `inactive` — temporarily unavailable for pickup
- `transferred` — left the school because of transfer
- `graduated` — completed the school program
- `archived` — retained for history but removed from active operations

Legacy student documents with no `status` field are treated as `active`.

## Safety behavior

When a school admin changes a student from `active` to another status:

1. the student document is updated with the new status, reason, actor, and server timestamp;
2. unused pickup QR tokens for that student are immediately invalidated;
3. the change is added to the audit log;
4. QR verification rejects non-active students;
5. token issuance rejects non-active students;
6. the live dismissal dashboard excludes non-active students;
7. parent and teacher Android rosters hide non-active students from normal pickup operations.

Historical exit logs are not deleted or changed.

## Android admin UI

Open:

`School Admin -> Student Lifecycle & Promotion`

The screen provides:

- All / Active / Inactive / Transferred / Graduated / Archived filters
- name or LRN search
- status counts
- per-student status change
- optional reason/note
- end-of-year promotion preview
- promotion confirmation only when every active student can be mapped safely

## End-of-year promotion

The preview tries to map an active student to the next numeric grade in the target school year while keeping the same section name.

Examples:

- `4 / Rizal` -> `5 / Rizal`
- `Grade 4 / Rizal` -> `Grade 5 / Rizal`

If a matching active target section does not exist, that student is reported as unresolved and **no bulk promotion is executed**.

This makes the operation fail-safe rather than silently placing students in the wrong class.

For schools that rename sections between years, the backend already accepts explicit `sectionMappings` (`sourceGradeSectionId -> targetGradeSectionId`). A richer mapping UI can be added later without changing the promotion API.

## Firestore

No new composite Firestore index is required for this update.

## New backend endpoint group

- `GET /api/school-admin/students/lifecycle`
- `PUT /api/school-admin/students/{studentId}/status`
- `POST /api/school-admin/students/promote`

All three endpoints require `school_admin` and enforce the authenticated user's `schoolId` server-side.
