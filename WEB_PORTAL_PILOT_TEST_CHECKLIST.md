# PickupPass Web Portal Pilot Test Checklist

Run this after Firebase Hosting is deployed and before calling the web portal pilot-ready.

## General
- [ ] Login page is responsive on desktop and phone widths.
- [ ] Parent, Teacher/Staff, School Admin, and Master Admin route to the correct role home.
- [ ] Role navigation highlights the current section.
- [ ] System/Light/Dark theme toggle works and persists.
- [ ] Sign out returns to login.
- [ ] Browser console has no uncaught application errors during normal navigation.

## Parent
- [ ] Students load only for the signed-in parent.
- [ ] Generate a new pickup QR.
- [ ] Add and remove a permanent backup guardian.
- [ ] Add a one-day temporary guardian.
- [ ] Add/change/remove a recurring guardian pickup schedule.
- [ ] Device/session list loads; revoke a test session.
- [ ] Notifications and profile work.

## Teacher / Staff
- [ ] Scanner opens and camera permission is handled.
- [ ] 0 active gates: scan works without gate selection.
- [ ] 1 active gate: gate is selected automatically.
- [ ] 2+ active gates: available gates can be selected; optional restrictions are respected.
- [ ] Valid QR verifies and approves exactly once.
- [ ] Replay of the same QR is rejected.
- [ ] Exit history reflects the approved release.
- [ ] Student/guardian and announcement pages work.

## School Admin
- [ ] Dashboard loads current-day dismissal metrics.
- [ ] Academic years/sections and student lifecycle pages load.
- [ ] Bulk import validates a sample file before committing.
- [ ] Staff management loads and actions are authorized.
- [ ] Campus/gate settings work without making gates mandatory.
- [ ] Manual release requires student, authorized guardian, reason, and confirmation; gate rule is respected.
- [ ] Reports load and CSV export downloads.
- [ ] Scheduled announcement flow works.
- [ ] Billing center loads invoice/payment status; downloads work where records exist.
- [ ] Audit page loads.
- [ ] Launch Readiness can save manual checks and request review when eligible.

## Master Admin
- [ ] Tenant list and plan/feature controls load.
- [ ] Usage/quota information loads.
- [ ] Billing ledger and pending GCash review load.
- [ ] Operations health/security/observability/DR panels load according to configured features.
- [ ] Launch review can approve/reopen an eligible test school.

## Final safety regression
- [ ] Wrong-school QR rejected.
- [ ] Removed/suspended guardian's old QR rejected.
- [ ] Non-active student cannot be released.
- [ ] Simultaneous approval attempt results in one release/one rejection and only one exit log.
- [ ] Revoked staff session cannot continue privileged actions.
