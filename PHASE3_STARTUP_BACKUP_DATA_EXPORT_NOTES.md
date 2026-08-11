# Phase 3 Update 12 — Startup Backup Mode & Tenant Data Export

## Role split

PickupPass keeps cloud-level disaster recovery under the **platform owner / master_admin**. A school/tenant administrator cannot enable Firestore backup schedules, PITR, restore databases, or change retention because those settings affect the shared production project and can create platform-wide cost or risk.

A **school_admin** may optionally create a tenant-scoped direct-download export only when the platform owner has enabled `selfServiceDataExportEnabled` for that school. The platform owner may always create an export for support/recovery.

## Startup-first protection profiles

- **Free safeguards**: Firestore database delete protection only. No scheduled backup or PITR is created by PickupPass.
- **Startup backup (recommended initially)**: delete protection + one daily native Firestore backup with short retention (default 7 days). No PITR or weekly schedule is created.
- **Growth protection**: daily + weekly backups, PITR, and delete protection. This is still available but is deliberately opt-in.

Profiles are additive/safe: selecting Free or Startup does not silently disable stronger protection that is already enabled. If PITR or a weekly schedule already exists, the console warns the platform owner to review Google Cloud billing before intentionally removing it.

## Tenant data export

School self-service export is OFF by default. The platform owner can enable/disable it per tenant from the Master Admin tenant card.

Exports are generated on demand and downloaded directly through Android's system document picker. PickupPass does not create a recurring Cloud Storage copy. This avoids a standing storage charge and keeps the startup workflow simple.

The ZIP contains tenant-scoped operational records:

- school profile/configuration
- students
- tenant users
- academic years and grade/section structure
- campuses and pickup gates
- dismissal exit logs
- school audit events

The export excludes platform billing/security internals and redacts token/secret-like fields. Device sessions, pickup tokens, payment-webhook events, platform security telemetry, idempotency keys, and SaaS operations metadata are not included.

This export is a portability/support backup, **not an automatic restore package**. Production restore remains a platform-owner process.

## Cost/read guardrails

Defaults:

```text
TENANT_DATA_EXPORT_MAX_DOCUMENTS=25000
TENANT_DATA_EXPORT_MAX_ARCHIVE_BYTES=52428800
FIRESTORE_DR_DEFAULT_PROFILE=startup
FIRESTORE_DR_DAILY_RETENTION_DAYS=7
```

The document/archive caps bound unexpected reads and server memory usage. Raise them only after measuring real tenant size.

## New endpoints

Platform owner:

```text
PUT /api/master-admin/schools/{schoolId}/data-export-access
GET /api/master-admin/schools/{schoolId}/data-export
POST /api/master-admin/disaster-recovery/protection/free
POST /api/master-admin/disaster-recovery/protection/startup
POST /api/master-admin/disaster-recovery/protection/recommended   # growth
```

School admin:

```text
GET /api/school-admin/data-export/status
GET /api/school-admin/data-export/download
```

## Safety boundary

Backup, export, billing, or Google Cloud Admin API failures never participate in QR verification or student release.
