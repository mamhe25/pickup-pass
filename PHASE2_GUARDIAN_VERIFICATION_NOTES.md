# Phase 2 — Guardian Identity Verification

This cumulative update adds an optional school-admin guardian verification workflow without changing PickupPass's direct QR pickup model.

## School Admin → Guardian Verification

School admins can:
- turn **Require verified guardians** on or off for their school;
- see every linked parent/guardian and the students they are authorized to pick up;
- mark a guardian **Pending**, **Verified**, or **Suspended**;
- record a verification/suspension note;
- immediately invalidate unused QR passes when a guardian is moved to Pending or Suspended.

## Compatibility rules

- The feature is **off by default**.
- Existing/legacy guardians without a verification status are treated as Verified, so enabling this update does not lock existing families out.
- When verification is required, a new guardian added by another parent starts as Pending until the school verifies them.
- Guardians added by school staff are trusted as Verified.
- Suspended guardians cannot use pickup access even if the school-wide verification requirement is off.
- Guardian authorization is checked again by the backend when a QR is issued/scanned; the client cannot bypass the status.

## Firestore fields

`schools/{schoolId}`
- `guardianVerificationRequired`
- `guardianVerificationUpdatedAt`
- `guardianVerificationUpdatedBy`

`users/{guardianUid}`
- `guardianVerificationStatus`: `pending | verified | suspended`
- `guardianVerificationReason`
- `guardianVerificationUpdatedAt`
- `guardianVerificationUpdatedBy`
- `guardianVerifiedAt`
- `guardianVerifiedBy`

No new Firestore index is required for this feature.
