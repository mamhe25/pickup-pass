# Phase 3 Update 3 — Usage Metering & Plan Quota Enforcement

This update turns the plan limits introduced in Update 2 into enforced SaaS quotas.

## Enforced active-resource limits
- Active students
- Active staff (`teacher` + `school_admin`)
- Active campuses

Quota reservations use a Firestore transaction against `tenantUsage/{schoolId}` so concurrent requests cannot both consume the final available slot.

## Existing schools
Usage counters are lazily reconciled from current Firestore records the first time they are needed. New schools start with zeroed counters immediately.

## Lifecycle behavior
- Creating/importing/reactivating students consumes student slots.
- Inactivating/transferring/graduating/archiving an active student releases a student slot.
- Inviting/creating/reactivating staff consumes staff slots; deactivation releases a slot.
- Creating/reactivating a campus consumes a campus slot; archiving releases one.
- Downgrading a plan never deletes or deactivates existing records. If a tenant is already above the new limit, further additions/reactivations are blocked until usage returns below the limit or the plan is upgraded.

## Metering
Successful QR pickups and manual overrides increment lifetime counters and monthly counters under `tenantUsage/{schoolId}/months/{YYYY-MM}`. Metering is intentionally best-effort and cannot turn an already-approved student release into a failed pickup.

## Master Admin
Tenant cards now show current students/staff/campuses against plan limits plus lifetime QR/manual pickup totals.

No new composite Firestore index is required.
