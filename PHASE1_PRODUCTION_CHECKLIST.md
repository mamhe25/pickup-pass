# PickupPass Phase 1 Production Verification

Run this from PowerShell at the project root after copying the cumulative update:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\phase1-verify.ps1
```

To also validate production environment variables:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\phase1-verify.ps1 -ProductionChecks
```

## Required before launch

- Backend tests pass.
- Android debug build completes without compile errors.
- Production Spring profile is active.
- QR signing and bootstrap secrets are unique and at least 32 characters.
- CORS contains only real HTTPS application domains.
- Firebase service credentials are supplied through the deployment environment, not committed to source control.
- Test teacher session revocation: revoke all sessions, then confirm the next protected API call returns the user to Login.
- Test duplicate dismissal: approve one student, then attempt a second release for the same student on the same school business date; the second attempt must be rejected.
- Test QR replay: approve a QR once, then scan/approve the same token again; it must not create a second exit log.
- Test idempotent retry: send the same approval request twice with the same `Idempotency-Key`; the second response must return the original `exitLogId`.
- Test cross-school isolation with two test schools. A teacher/admin from School A must not access or dismiss School B students.
- Remove a guardian, then verify any old QR for that guardian is rejected.
- Test manual override using a school admin. Require an authorized guardian and a meaningful reason, and confirm the audit event is present.
- Confirm `/actuator/health/liveness` and `/actuator/health/readiness` are usable by your deployment health checks.
- Confirm Crashlytics receives a non-production test event before release.

## Manual concurrency test

Use two staff devices/accounts and attempt to approve the same student at nearly the same time. Exactly one release should succeed. The second request should receive a conflict because the daily dismissal lock already exists.

## Deployment gate

Do not deploy if any of these remain true:

- Maven/backend tests fail.
- Android does not compile.
- A wildcard CORS origin is enabled.
- Development/default secrets are still configured.
- Cross-school data can be read or modified.
- One student can obtain two exit logs for the same business date through QR/manual approval races.
- Revoked staff sessions continue accessing protected APIs.


## Backup & disaster recovery readiness (Phase 3 Update 11)

- [ ] Configure the intended Google Cloud project/database and least-privilege DR identity before setting `FIRESTORE_DR_ENABLED=true`.
- [ ] Confirm daily + weekly Firestore backup schedules are active in Google Cloud.
- [ ] Confirm PITR and Firestore database delete protection are enabled.
- [ ] Confirm at least one READY backup is younger than the configured health threshold.
- [ ] Run and verify an isolated restore drill before production launch; never test recovery by overwriting production.
- [ ] Document recovery owner, escalation path, RTO/RPO target, cutover procedure, and temporary recovery-database cleanup.
- [ ] Enable Firestore TTL only for approved ephemeral fields such as `securityAuthWindows.expiresAt` and `idempotencyKeys.expiresAt`.
- [ ] Keep student release, audit, and financial-record deletion governed by an explicit retention policy rather than automatic application cleanup.
