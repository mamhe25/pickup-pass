# Phase 2 Update 11 — Scanner Pickup Gate Integration

This update connects configured campuses and pickup gates to actual student release records.

## Behavior

- If the school has no active pickup gates, the scanner behaves exactly as before.
- If active pickup gates exist, staff must select the physical release gate before scanning/approving.
- A single active gate is selected automatically.
- The server validates that the selected gate belongs to the authenticated staff member's school and is active.
- If the gate belongs to a campus, the campus must also be active.
- Gate selection is included in the idempotency fingerprint, so the same request key cannot be replayed with a different release location.
- Manual pickup overrides also require a gate when the school has active gates.

## Exit log snapshots

New exit logs can contain:

- pickupGateId
- pickupGateNameSnapshot
- campusId
- campusNameSnapshot

Historical records keep the gate/campus names from the time of release even if an administrator later renames or archives a location.

## Dashboard and exports

- Recent releases on the school dashboard show the recorded release location when available.
- Dismissal CSV exports include Campus and Pickup Gate columns.
- Older exit logs remain compatible and simply have blank location columns.

## Firestore indexes

No new composite index is required for this update.
