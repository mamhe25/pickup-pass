# PickupPass Phase 3 Update 10 — Master Admin Audit & Security Center

## Purpose
Update 10 adds a privacy-preserving security operations layer for the SaaS operator. It is designed to surface meaningful authentication/session/admin-risk signals without storing raw Firebase tokens, raw passwords, raw GCash data, or raw client IP addresses.

## Security center
Master Admin can review:
- Active security alerts (open + acknowledged) by severity.
- Repeated invalid Firebase-token attempts.
- Revoked device-session reuse attempts.
- Privileged API access-denied events.
- Protected endpoint rate-limit events.
- Recent platform-level privileged audit actions from `systemAuditEvents`.

## Privacy-preserving request fingerprinting
PickupPass HMACs the source address + User-Agent using `SECURITY_FINGERPRINT_SECRET`. Only the keyed fingerprint is stored for correlation. Configure a separate random 32+ character secret in production; do not reuse the QR signing or bootstrap secret.

## Cost-aware aggregation
Invalid-token attempts are grouped into 15-minute security windows. An alert is materialized only after repeated attempts from the same keyed fingerprint, rather than writing a full alert for every invalid request.

## Session containment
Master Admin can revoke all registered device sessions plus Firebase refresh tokens for a specific UID. A reason is required and the action is written to the platform audit log.

## New backend endpoints
- `GET /api/master-admin/security/overview`
- `POST /api/master-admin/security/alerts/{alertId}/status`
- `POST /api/master-admin/security/users/{uid}/revoke-sessions`

All require `master_admin`.

## Firestore records
- `securityAlerts/{deterministicHash}` — materialized/deduplicated security alerts.
- `securityAuthWindows/{hash}` — short-window aggregate counters for repeated invalid authentication attempts.
- Existing `systemAuditEvents` remains the source for privileged Master Admin action history.

No new composite Firestore index is required.

## Operational boundary
Security monitoring must never be placed in the successful QR release transaction. Telemetry failure is non-blocking. Existing authentication/session enforcement still remains fail-closed where appropriate, but writing a security alert cannot itself prevent an otherwise valid student release.

## Telemetry retention

`securityAuthWindows` records now include an `expiresAt` field seven days after the aggregation window. For production cost hygiene, enable a Firestore TTL policy on `securityAuthWindows.expiresAt`. TTL is only for short-lived authentication telemetry; `securityAlerts` and audit events are not automatically deleted. The request fingerprint is used only for alert aggregation and never as an authentication or authorization decision.
