# Phase 3 Update 14 — Startup Observability & Incident Readiness

## Goal
Provide useful production visibility without requiring a paid APM/observability service during PickupPass startup stage.

## Responsibility model
Infrastructure/runtime health is PLATFORM OWNER / `master_admin` only. School admins do not receive JVM, Firestore connectivity, error-rate, memory, or platform incident controls because those are shared SaaS infrastructure details.

## Low-cost architecture
- HTTP request counts, 4xx/5xx counts, duration, and slow-request counts live only in backend memory.
- `/actuator/health/**` remains available for basic liveness/readiness checks.
- `/actuator/metrics/**` and `/actuator/info` require `master_admin` authentication.
- Firestore receives no per-request observability write.
- `platformIncidents` is written only when an incident becomes active, is acknowledged/resolved, or auto-resolves.
- No third-party APM, uptime SaaS, log aggregation subscription, Cloud Storage, or additional database is required.

Because rolling HTTP metrics are in-memory, they reset whenever a backend instance restarts. That is intentional for startup mode. Durable incident state remains in Firestore when enabled.

## Automatic conditions
The default evaluator runs every five minutes and checks:
- Firestore reachability (two consecutive failed checks before the live state is treated as degraded)
- rolling HTTP 5xx rate (10% with at least 20 requests in the 15-minute window)
- rolling slow-request rate (25%, where slow defaults to >= 2000 ms)
- JVM memory use (85% of max heap)

Thresholds are environment-configurable.

## Firestore outage behavior
If Firestore is unavailable, PickupPass cannot reliably persist an incident about Firestore being unavailable. The Master Admin overview therefore also exposes an in-memory `runtime-firestore-connectivity` incident. It is clearly marked as runtime-only and cannot be acknowledged until Firestore is available.

Observability failure is never an authorization dependency for QR verification or student release.

## New Master Admin APIs
- `GET /api/master-admin/observability/overview`
- `POST /api/master-admin/observability/evaluate`
- `POST /api/master-admin/observability/incidents/{incidentId}/status`

All require `master_admin`.

## New Firestore collection
`platformIncidents/{deterministicIncidentId}`

No new composite Firestore index is required.

## Startup defaults
```text
OBSERVABILITY_WINDOW_MINUTES=15
OBSERVABILITY_MINIMUM_REQUESTS=20
OBSERVABILITY_SERVER_ERROR_RATE_PERCENT=10
OBSERVABILITY_SLOW_REQUEST_RATE_PERCENT=25
OBSERVABILITY_SLOW_REQUEST_MS=2000
OBSERVABILITY_MEMORY_WARNING_PERCENT=85
OBSERVABILITY_FIRESTORE_FAILURE_THRESHOLD=2
OBSERVABILITY_INITIAL_DELAY_MS=60000
OBSERVABILITY_EVALUATE_MS=300000
OBSERVABILITY_DURABLE_INCIDENTS_ENABLED=true
```

Set `OBSERVABILITY_DURABLE_INCIDENTS_ENABLED=false` if you want zero Firestore writes from this incident subsystem. The live in-memory platform metrics still work, but acknowledgement/history will not be durable.

## Recommended startup operation
1. Keep the default low-cost mode.
2. Use the Master Admin `Check now` button after deployment or when a school reports trouble.
3. Keep normal backend structured logs available from your hosting provider.
4. Configure a basic external uptime check only if your hosting provider includes one free; it is not required by PickupPass.
5. Do not purchase a full APM product until traffic/customer count justifies it.

## Future growth path
The current service can later feed Micrometer/OpenTelemetry or an external APM without changing PickupPass business logic. Do not make an external monitoring vendor part of the QR pickup transaction path.
