# Phase 2 Update 6 — One-Day Temporary Guardian Authorization

This update adds a production-safe one-day pickup authorization without changing PickupPass's normal direct-QR pickup flow.

## Parent workflow

Authorized permanent guardian -> Authorized Guardians -> Authorize One-Day Pickup -> enter guardian details and pickup date.

Rules:
- Valid for one calendar date only.
- Can be created for today through 30 days ahead.
- One successful pickup only.
- Temporary guardian gets/uses their own parent account and QR.
- QR generation is rejected before or after the authorized date.
- QR verification re-checks temporary authorization at scan time.
- After successful QR or manual-override pickup, temporary access is removed atomically from the student record.
- Reissuing temporary authorization invalidates old unused QR tokens.
- Temporary guardians cannot add, remove, or authorize other guardians.
- Existing permanent guardians remain unchanged.

## Backend endpoint

POST /api/parent/add-temporary-guardian

Example body:
{
  "studentId": "student-id",
  "guardianEmail": "relative@example.com",
  "lastName": "Cruz",
  "firstName": "Roberto",
  "relationship": "uncle",
  "validDate": "2026-08-12"
}

## Firestore guardian entry

guardians.<uid>:
- relationship
- isPrimary: false
- authorizationType: temporary
- validDate: YYYY-MM-DD
- remainingUses: 1
- addedBy
- addedAt

No new Firestore index is required.
