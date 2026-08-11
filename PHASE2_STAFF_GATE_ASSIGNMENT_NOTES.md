# Phase 2 Update 12 — Staff Pickup Gate Assignments

This update lets school administrators restrict individual teacher/school-admin scanner access to selected active pickup gates.

Behavior:
- Empty assignment = all active pickup gates (backward compatible default).
- One or more assigned gate IDs = scanner only receives those active gates.
- Backend approval independently validates the submitted gate against the staff assignment.
- Archived gates/campuses are never returned to scanners.
- Assignment changes are recorded in the audit log as `staff.pickup_gates_updated`.
- Existing staff accounts require no migration.

Admin path:
School Admin -> Staff Pickup Gates

Firestore field added to users/{uid}:
- assignedPickupGateIds: string[]
- pickupGateAssignmentUpdatedAt
- pickupGateAssignmentUpdatedBy

No new composite Firestore index is required.
