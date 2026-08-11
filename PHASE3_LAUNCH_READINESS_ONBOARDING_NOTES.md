# PickupPass Phase 3 Update 13 — Startup School Onboarding & Launch Readiness

## Purpose
This update adds a low-cost, on-demand readiness wizard for onboarding a real school without introducing another paid cloud service or recurring background scan.

## Role boundary
- `school_admin`: completes tenant-scoped configuration, performs the on-site manual checks, and requests launch review.
- `master_admin` / platform owner: performs the final review and grants or reopens launch approval.
- Tenant admins cannot change shared cloud infrastructure, Firestore backup/PITR, billing infrastructure, or platform security settings.

This split is intentional. The school knows whether its scanner device, staff briefing, and emergency procedure are actually ready. The platform owner remains accountable for deciding whether a tenant is approved for production.

## Cost model
The readiness assessment runs only when the school admin or platform owner opens/refeshes the wizard. It uses ordinary tenant-scoped Firestore reads and one small `schoolLaunchReadiness/{schoolId}` document for manual confirmations/review state. There is no scheduler, Cloud Storage copy, external SaaS dependency, or new paid service.

## Required automatic launch checks
1. Tenant is active.
2. Trial/subscription access is currently usable.
3. At least one active `school_admin` exists.
4. A current academic year exists.
5. At least one active grade/section exists for the current year.
6. At least one active student exists.
7. Every active student has at least one authorized guardian.

## Recommended warnings (not launch blockers)
- Students with incomplete academic placement.
- No dedicated teacher/pickup-staff account yet (school admin can still scan).
- School logo not configured.

Pickup gates remain optional by product design:
- 0 gates: scanner works normally without gate selection.
- 1 active gate: auto-selected.
- 2+ active gates: staff can select among allowed gates.

## Required on-site manual confirmations
The school admin confirms:
- scanner/device test completed;
- guardian QR flow tested end-to-end;
- dismissal staff briefing completed;
- emergency/fallback pickup procedure reviewed.

These checks are manual because the backend cannot reliably prove camera permissions, real device connectivity, staff understanding, or the school's offline/emergency procedure.

## Review lifecycle
`draft` → school completes all required checks → `review_requested` → platform owner verifies → `approved`.

If an approved tenant later develops a required blocker, the dynamic assessment returns `approved_needs_attention`. Approval is therefore not treated as proof that current configuration can never regress.

## New Firestore record
`schoolLaunchReadiness/{schoolId}`

Stores only onboarding state/manual confirmations and review metadata. It does not duplicate student rosters, guardian records, or billing data.

## New endpoints
School admin:
- `GET /api/school-admin/launch-readiness`
- `PUT /api/school-admin/launch-readiness/manual-checks`
- `POST /api/school-admin/launch-readiness/request-review`

Platform owner:
- `GET /api/master-admin/schools/{schoolId}/launch-readiness`
- `POST /api/master-admin/schools/{schoolId}/launch-readiness/approve`
- `POST /api/master-admin/schools/{schoolId}/launch-readiness/reopen`

## Firestore indexes
No new composite index is required. The assessment uses existing tenant fields and single-field `schoolId` queries.

## Safety boundary
Launch-readiness state is an onboarding/operations control. It is intentionally not checked by QR issuance, QR verification, pickup approval, or exit-log creation. A readiness UI/API problem must never interrupt an already-authorized student release.
