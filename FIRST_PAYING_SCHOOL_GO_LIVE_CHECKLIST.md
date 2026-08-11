# PickupPass — First Paying School Go-Live Checklist

Use this after applying Phase 3 Update 15.

## Platform owner

- [ ] Production backend builds successfully with tests.
- [ ] Android release build succeeds with the real signing key.
- [ ] Hardened Firestore rules are deployed.
- [ ] Current Firestore indexes are deployed.
- [ ] `SPRING_PROFILES_ACTIVE=prod`.
- [ ] QR signing secret is unique and 32+ random characters.
- [ ] Security fingerprint secret is separate and 32+ random characters.
- [ ] `BOOTSTRAP_ENABLED=false`.
- [ ] No production service-account JSON or signing key exists inside the repository.
- [ ] Billing/GCash receiver configuration is correct.
- [ ] Startup backup profile is either intentionally OFF or configured at the desired low-cost level.
- [ ] Master Admin observability/security dashboards open successfully.

## School

- [ ] School admin account works.
- [ ] Current academic year is selected.
- [ ] Grade/section structure is correct.
- [ ] Active student roster is imported.
- [ ] Every active student has at least one authorized guardian.
- [ ] Pickup gates are intentionally left unconfigured, single-gate auto-selected, or correctly configured for multi-gate operation.
- [ ] Scanner device has camera permission and stable connectivity.
- [ ] Guardian QR has been tested end-to-end.
- [ ] Used/duplicate QR is rejected.
- [ ] Wrong-school QR is rejected.
- [ ] Staff know normal and emergency/manual procedures.
- [ ] Platform owner reviews and approves Launch Readiness.

## Go / No-Go rule

Do not onboard live students if any of these fail:

- tenant isolation / wrong-school QR;
- guardian authorization;
- QR consumption / duplicate prevention;
- staff authentication;
- Firestore rule deployment;
- production backend build;
- launch-readiness required blockers.

Billing reminders, advanced reports, optional backup profiles and cosmetic branding warnings are not student-safety launch blockers.
