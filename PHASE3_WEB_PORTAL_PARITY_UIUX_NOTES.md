# PickupPass Phase 3 Update 16 — Web Portal Feature Parity + UI/UX Modernization

## Purpose
Update 16 brings the browser experience up to the level of the backend and Android work completed across Phases 1–3. It also refreshes the older pre-Phase-1 web screens so Parent, Teacher/Staff, School Admin, and Master Admin share one professional, modern, responsive design language.

## UI/UX modernization
- New shared portal shell for all signed-in web roles.
- Sticky glass-style role navigation with consistent active states and school identity.
- System / Light / Dark theme toggle on signed-in role portals.
- Responsive page headers, KPI cards, tables, status pills, form controls, alerts, and action groups.
- Progressive reveal motion with reduced-motion support.
- Modern split-screen login experience.
- Consistent empty/loading/error feedback and non-blocking toast messages.
- Professional SVG iconography in shared navigation.
- New default avatar SVG; no dependency on a missing PNG placeholder.
- Existing legacy web screens inherit the same visual system instead of looking like a separate generation of the app.

## Parent web parity
- My Students and secure pickup pass flow.
- Permanent backup guardian management.
- One-day temporary guardian authorization.
- Recurring weekday/date-range guardian pickup schedules.
- Guardian removal and schedule removal.
- Device/session view, revoke one device, revoke other devices.
- Notifications and profile.
- Secured guardian profile lookup through the backend endpoint introduced by Release Candidate hardening.

## Teacher / Staff web parity
- QR scanner verification and approval flow.
- Pickup gate behavior remains: 0 gates = no selection, 1 gate = automatic, 2+ gates = selectable subject to optional restriction.
- Student roster, parent registration, guardian management, temporary guardian, guardian schedules.
- Exit history, announcements, notifications.
- Operations page for gate access, academic structure, and feature entitlements.

## School Admin web parity
- Live dismissal dashboard and pickup policy.
- Student lifecycle, bulk import, academic years, grades and sections.
- Staff management.
- Campuses and pickup gates; optional staff gate restrictions.
- Optional guardian verification policy.
- Emergency/manual release workflow with reason, confirmation, idempotency, guardian authorization, and gate rules.
- Dismissal reports and CSV export.
- Scheduled announcements and history.
- Subscription/billing center, invoice/receipt downloads, manual GCash payment notice workflow.
- Audit events.
- Launch Readiness workflow.
- Branding and tenant data export status/download where permitted.

## Master Admin web parity
- Tenant creation/status, subscription plans, feature overrides, usage/quota, export permission, launch review.
- Billing ledger and manual GCash payment review.
- Operations health, security alerts, session revocation, platform observability/incidents, and disaster-recovery controls.

## Hosting/API routing
Production web API calls now use same-origin `/api`. Firebase Hosting rewrites `/api/**` to the existing Cloud Run service `pickup-pass-backend` in `asia-southeast1`. Localhost continues to use `http://localhost:8080/api`.

This avoids hard-coding the current Cloud Run URL into every web build and keeps a single production web origin for browser requests.

## Safety boundaries preserved
- No mandatory pickup queue or parent arrival/check-in was added.
- QR verification/release remains the core dismissal path.
- Subscription/billing state does not become the authorization decision for a valid core pickup.
- Launch readiness, observability, billing, backup, and security telemetry remain outside the QR release transaction.
- Manual release stays School Admin controlled and audited.

## Deployment impact
- New database migration: none.
- New Firestore composite index: none.
- New Firestore rules required specifically by Update 16: none.
- New backend environment variable required specifically by Update 16: none.
- Firebase Hosting deployment: required to make the new web UI visible online.
- Backend source redeploy: not required specifically for Update 16 if the current fixed Update 15 backend is already live.
