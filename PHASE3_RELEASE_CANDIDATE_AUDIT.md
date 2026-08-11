# PickupPass Phase 3 Update 15 — Release Candidate Hardening & Audit

## Purpose

Update 15 is a stabilization release. It intentionally adds very little product surface area. The goal is to close launch-blocking security/configuration gaps before the first paying school is onboarded.

## Critical issues fixed in this update

### 1. Bootstrap could not be safely disabled in production

Earlier production validation required `BOOTSTRAP_SECRET` even after the first master administrator had already been created. That conflicted with the intended operational procedure of removing the bootstrap secret after first use.

**Fix:** bootstrap now has an explicit `BOOTSTRAP_ENABLED` flag which defaults to `false`. Production requires a strong bootstrap secret only while bootstrap is intentionally enabled.

Recommended sequence:

1. Deploy with `BOOTSTRAP_ENABLED=true` and a temporary 32+ character secret.
2. Create the first master administrator.
3. Set `BOOTSTRAP_ENABLED=false`.
4. Remove/rotate `BOOTSTRAP_SECRET`.
5. Redeploy.

### 2. Direct Firestore user updates were too broad

A signed-in user could previously update their own complete `users/{uid}` document through Firestore rules. Even though backend authorization relies on Firebase custom claims, allowing client-controlled changes to role/school/status fields is an unnecessary privilege-escalation surface.

**Fix:** direct user updates are limited to `photoUrl` only. Account role, school, status, FCM tokens, provisioning and administrative fields are backend-owned.

### 3. Parents could enumerate the same-school user directory

The old `users` read rule allowed any active user in a school to read other user documents from the same tenant.

**Fix:** direct reads of other users are now staff/master-admin only. Parent guardian screens use a new backend endpoint that verifies the student relationship and returns only `uid`, `displayName`, and `photoUrl` for guardians linked to that child.

### 4. Official write paths could be bypassed through direct Firestore writes

Direct client rules allowed writes to students, pickup tokens and exit logs. This could bypass quota/audit/lifecycle logic or allow a modified client to create records outside the intended backend workflow.

**Fix:** these mutations are now backend-only. Firestore Admin SDK writes remain unaffected because server credentials bypass client security rules.

### 5. Android release configuration allowed local cleartext hosts

The main network-security resource contained the local development allowlist, which also shipped in release builds.

**Fix:** the main/release configuration rejects all cleartext traffic. A debug-only resource contains localhost/emulator/LAN exceptions.

### 6. Production Docker image builds skipped tests

The Dockerfile used `-DskipTests`.

**Fix:** production image builds now use `mvn clean verify -B`; a failed backend test prevents creation of the release image.

### 7. Secret-file hygiene was incomplete

The root ignore rules did not explicitly block common service-account/signing files.

**Fix:** repository and Docker ignore rules now exclude `secrets/`, `.env*`, PEM/P12/JKS/keystore files. The RC verifier fails if non-empty credential material is found in the project tree.

## Startup-cost decisions preserved

This audit does **not** add a paid APM service, second database, Cloud Storage, PITR requirement, or recurring enterprise backup requirement.

The application continues to use the startup-first approach:

- optional low-cost backup controls;
- in-memory runtime metrics;
- Firestore writes only for durable incidents;
- manual GCash subscription reconciliation;
- backend-generated tenant exports;
- no mandatory pickup queue/check-in.

## Firestore rules deployment is launch-blocking

After applying Update 15, deploy the hardened rules before onboarding a real school.

From `pickup-pass-system`:

```powershell
firebase deploy --only firestore:rules
```

If indexes have not already been deployed from the latest cumulative package:

```powershell
firebase deploy --only firestore:indexes
```

## Local release-candidate verification

From the project root on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\phase3-rc-verify.ps1
```

Use `-SkipBuilds` for the static-only gate:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\phase3-rc-verify.ps1 -SkipBuilds
```

The script checks rule hardening, bootstrap defaults, Android release cleartext policy, production Docker test execution, configuration JSON, source-tree credential files, and—when tools exist—local Maven/Android builds.

## Before the first paying school

These are the remaining release activities rather than new feature development:

1. Run backend tests and Android debug/release builds on the real development machine.
2. Deploy hardened Firestore rules and current indexes.
3. Verify `SPRING_PROFILES_ACTIVE=prod`.
4. Verify strong unique QR and security-fingerprint secrets.
5. Confirm `BOOTSTRAP_ENABLED=false` after first master-admin provisioning.
6. Run one end-to-end guardian QR → staff scan → approval → exit-log test.
7. Run one duplicate/used QR rejection test.
8. Run one wrong-tenant QR rejection test.
9. Test one manual override with audit record.
10. Test one staff deactivation/session-revocation flow.
11. Test the school's Launch Readiness checklist before approving launch.
12. Confirm GCash payment review can never block an authorized student release.

## Intentionally deferred until there is traction

Do not delay first launch for these unless actual usage proves they are needed:

- paid APM/observability;
- automatic online payment gateway;
- PITR/enterprise recovery profile;
- Redis/distributed rate limiting;
- full web-dashboard rewrite;
- advanced data warehouse/BI;
- multi-region deployment.

## Release posture

**Update 15 should be treated as the first release-candidate hardening pack, not as automatic proof that production is safe.** A successful full local build plus the end-to-end launch checks above are still required before onboarding the first live school.
