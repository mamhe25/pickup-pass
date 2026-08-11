# Phase 3 Update 1 — Master Admin SaaS Console

This cumulative update starts the SaaS operations layer for PickupPass.

## Added
- Master-admin Android home screen and role routing.
- Tenant list with Active / Suspended status.
- Total, Active, and Suspended tenant counters.
- Create a new school tenant from the app.
- Suspend/reactivate a tenant using the existing server-side account lockout flow.
- Provision the initial `school_admin` account for a selected tenant.
- Master-admin tenant listing endpoint.

## Design note
The tenant list intentionally avoids scanning every student or exit log when the console opens. Detailed usage metering and subscription analytics should be maintained separately so the master console remains inexpensive as the SaaS grows.

## Pickup behavior
No parent pickup behavior changes. A valid QR remains sufficient for direct pickup during the school's allowed pickup rules. No queue/check-in was added.

## Firestore
No new composite index is required for this update.
