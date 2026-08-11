# PickupPass Phase 2 — Dismissal Reporting & Export

## Added

- `GET /api/school-admin/reports/dismissals/summary`
- `GET /api/school-admin/reports/dismissals/export`
- school-admin-only tenant isolation through the authenticated `schoolId`
- maximum report window of 366 days
- optional exact grade and section filters
- CSV formula-injection protection
- audit event on each report export
- Android **Dismissal Reports & Export** screen
- Android Save File flow for CSV output

## Report metrics

The summary returns total releases, unique students released, QR releases, manual overrides, daily counts, and grade/section counts for the selected date range.

## Historical snapshots

New exit logs store pickup-time identity/academic snapshots. Reporting prefers these immutable snapshot fields and falls back to current student/user records for older logs created before this update.

Snapshot fields:

- `studentNameSnapshot`
- `studentNumberSnapshot`
- `gradeSnapshot`
- `sectionSnapshot`
- `guardianNameSnapshot`
- `verifiedByNameSnapshot`

## Core pickup behavior

This update does not introduce a queue. QR pickup remains direct and parent-driven.
