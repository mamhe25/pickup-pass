# PickupPass Phase 2A — QR-first dismissal operations

This update preserves the original PickupPass behavior: **a parent does not need to join a queue or tap “I’m here” before pickup.** If the school leaves Pickup Policy on **Unrestricted** (the default), any currently-valid QR can be presented directly to staff and scanned.

## Added

- **Live Dismissal Dashboard** for school admins
  - total students
  - released today
  - still on campus
  - release percentage
  - 50 most recent releases
  - up to 250 still-on-campus students
  - automatic refresh every 30 seconds plus manual refresh
- **School Pickup Policy**
  - default: `unrestricted`
  - optional `time_window` mode using school timezone
  - optional school-wide manual-override enable/disable
- **Parent pickup-pass policy message**
  - explicitly tells parents that no queue/check-in is required
  - shows the school time window when one is enabled
- **Backend enforcement**
  - QR scan respects an optional school pickup time window
  - manual override respects the school’s override setting
- **Firestore composite index** for live dismissal queries
- **Audit event** whenever a school admin changes the pickup policy

## Important behavior

`unrestricted` is the default when a school has no `pickupPolicy` field yet. Existing schools therefore keep working without migration.

The current QR security rules still apply:

- QR must be signed and unexpired.
- QR must belong to the scanning school.
- Guardian must still be authorized.
- QR must not be used/superseded.
- A student cannot be dismissed twice on the same business date.

The new dashboard is status-only; it does not introduce a pickup queue.

## Firestore index deployment

This update changes both copies of `firestore.indexes.json`. From `pickup-pass-system`, deploy indexes before relying on the dashboard query in production:

```powershell
firebase deploy --only firestore:indexes
```

Wait until the new `exitLogs(schoolId, businessDate, timestamp DESC)` index finishes building in Firebase.

## Cumulative Update 5 — Reporting and export

School administrators now have a tenant-isolated **Dismissal Reports & Export** screen with date-range summary metrics and CSV export. New exit logs also preserve pickup-time student, guardian, grade/section, and approving-staff snapshots so later profile or academic changes do not alter future historical reports. The original direct QR pickup workflow remains unchanged.
