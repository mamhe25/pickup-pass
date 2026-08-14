# PickupPass Expert Review and Implementation Roadmap

Reviewed: 2026-08-14

## Executive assessment

PickupPass has a strong product premise and a broad implementation: a Spring Boot API, Firebase-backed web portal, and native Android client cover the core school pickup workflow plus tenant administration, billing, reporting, security operations, and recovery features.

The backend's automated baseline is healthy: all 49 tests pass. The main delivery risk is production discipline. Generated binaries, crash logs, Firebase cache data, and Maven output are committed, while the Android Gradle launcher is absent. Releases are therefore difficult to reproduce and meaningful source changes are obscured.

## Priority findings

### P0 — Release and credential hygiene

- Android release artifacts and Firebase/Maven generated files are tracked in Git.
- Signing keystores appear in repository history/status. Any real release key that entered shared history should be treated as exposed and rotated before public distribution.
- `google-services.json` is tracked. Firebase client API keys are identifiers rather than server secrets, but environment-specific client configuration should be injected per environment to prevent cross-environment releases.
- JVM crash and replay logs add noise and can contain local machine details.

### P0 — Android reproducibility

- `gradle/wrapper/gradle-wrapper.properties` exists, but `gradlew`, `gradlew.bat`, and the wrapper JAR do not.
- A clean machine cannot run the documented Android build without a separately installed Gradle version.
- Existing APKs are not a substitute for a reproducible source build.

### P1 — Test depth and release gates

- Backend coverage is concentrated around selected safety-critical paths rather than the full API surface.
- No Android unit-test source set was found during the initial inventory.
- The static web portal has no visible automated smoke, accessibility, or end-to-end suite.
- A release gate should build all clients, run tests, validate Firebase rules, and reject committed secrets/generated outputs.

### P1 — Frontend maintainability

- Many standalone HTML pages duplicate application concerns. Shared scripts and styles help, but feature behavior remains difficult to type-check, unit-test, and refactor safely.
- Continue shipping the current portal for the pilot, but move shared API/auth/state behavior into tested modules before adding more screens.

### P1 — Production configuration

- The backend has good production fail-fast validation and restrictive Firestore rules.
- Development defaults for QR signing and security fingerprinting are safe only while production validation remains mandatory in every deployment path.
- Deployment should explicitly activate the `prod` profile and supply secrets from a managed secret store.

### P2 — Product and operational focus

- The feature surface is large for a first paying-school launch. Prioritize the dismissal safety loop, onboarding, supportability, and measurable pilot outcomes over more administrative features.
- Define targets for scan-to-decision latency, approval success, notification delivery, incident response, recovery point, and recovery time.

## Phased implementation plan

### Phase 1 — Clean, reproducible foundation (in progress)

1. Expand ignore rules for build outputs, releases, Firebase cache, crash logs, client config, and signing material.
2. Preserve current tracked changes; remove generated files from tracking only in a dedicated reviewed cleanup.
3. Restore the official Gradle wrapper and verify Android tests/build from a clean checkout.
4. Rotate any signing credential that entered shared history and store replacements outside Git.
5. Add continuous integration for backend tests, Android tests/build, Firebase rules, and repository hygiene.

Exit criteria: a clean checkout produces verified artifacts without undocumented dependencies, and normal builds do not dirty Git.

### Phase 2 — Safety and tenant-isolation assurance

1. Add authorization matrix tests for every role and tenant boundary.
2. Add Firebase emulator rule tests for cross-school and guardian access.
3. Add concurrency/idempotency tests for issue, verify, approve, revoke, and replay.
4. Add negative tests for suspended accounts, expired sessions, malformed webhooks, and rate limits.

Exit criteria: tests prove that tenants cannot cross boundaries and passes cannot be reused under races.

### Phase 3 — Pilot reliability and observability

1. Add dismissal-loop dashboards and alert thresholds.
2. Add web and Android smoke tests for login, pass generation, scanning, approval, and notification history.
3. Exercise restore/export procedures and document an incident runbook.
4. Add release versioning and client/backend compatibility checks.

Exit criteria: pilot incidents are detectable, diagnosable, and recoverable with clear targets and owners.

### Phase 4 — UX and accessibility hardening

1. Test parent/teacher journeys on low-end devices and weak networks.
2. Improve offline/error recovery, loading states, large text, contrast, keyboard navigation, and screen-reader labels.
3. Consolidate shared web behavior into modular, tested code.

Exit criteria: critical journeys pass an agreed usability and accessibility checklist.

### Phase 5 — Scale and commercial readiness

1. Load-test dismissal peaks and establish tenant capacity limits.
2. Validate billing, retention, privacy notices, and support workflows.
3. Add staged rollout, rollback, and school-level feature controls.

Exit criteria: additional schools can be onboarded without disproportionate operational risk.

## Phase 1 change note

Ignore policy is updated, the official Gradle wrapper is restored, and automated repository/backend/Android quality gates are included. Generated artifacts are removed from Git tracking without deleting local copies. Firebase client configuration remains tracked temporarily so clean Android builds continue to work; environment injection will replace it in a dedicated migration.
