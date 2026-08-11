# Phase 2 — Bulk Student Import

## What this update adds

- School Admin > Bulk Import Students screen.
- CSV, XLSX and XLS roster uploads.
- Two-step safety flow: server preview/validation first, explicit import second.
- Maximum 5,000 roster rows and 10 MB per file.
- Required columns: `firstName`, `lastName`, `grade`, `section`.
- Optional columns: `studentNumber` (or LRN), `middleInitial`, `suffix`.
- Header aliases supported (for example `LRN`, `surname`, `givenName`, `gradeLevel`, `sectionName`).
- Validates grade/section against the active sections in the current school year when structured sections exist.
- Duplicate protection using student number/LRN when present, otherwise normalized name + grade + section.
- Existing duplicates are skipped instead of recreated.
- No partial import when the file contains invalid rows. Fix the file and preview again.
- Firestore writes are chunked below the batch operation limit.
- Successful imports are written to the audit log.

## Recommended workflow

1. Configure School Year & Sections.
2. Open School Admin > Bulk Import Students.
3. Choose a CSV/XLS/XLSX roster.
4. Review the validation totals and row errors.
5. Fix invalid rows if any and upload again.
6. Tap Import only when the preview reports the file is ready.

A sample `student_import_template.csv` is included in this update pack.
