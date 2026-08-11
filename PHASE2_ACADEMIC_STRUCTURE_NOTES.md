# Phase 2 Update 2 — Structured Academic Management

This cumulative update adds production-oriented academic structure management without changing PickupPass's core pickup behavior. A parent can still present a valid QR directly for pickup; no arrival queue is required.

## Added

- School-admin **School Year & Sections** screen.
- Academic year creation and current-year selection.
- Structured grade/section creation per academic year.
- Archive/reactivate grade sections instead of deleting historical structure.
- Teacher section assignments now use active configured sections for the current school year.
- Backend tenant checks on all academic-structure mutations.
- Audit events for academic-year and grade-section changes.
- Student creation now resolves/validates grade/section against configured school structure while keeping backward compatibility for schools that have not configured it yet.

## Firestore collections

- `academicYears/{id}`
- `gradeSections/{id}`

Both include `schoolId` for explicit tenant isolation.

## Migration behavior

Existing schools continue working. Once a school creates structured grade/sections, new teacher assignments and student placements must match an active configured section.

Existing student records retain their `grade` and `section` strings so current roster, scanner, dashboard, broadcasts, and exit logs keep working.
