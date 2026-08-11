# PickupPass Phase 3 Update 11 — Backup, Disaster Recovery & Data Retention

## Objective

Update 11 adds an operator-facing disaster-recovery layer without putting recovery logic in the student pickup transaction. PickupPass uses Google Cloud Firestore native backup/PITR/delete-protection capabilities rather than copying production documents into a second Firestore collection and calling that a backup.

## Recommended startup protection profile

The application defaults to the following requested protection profile when the Master Admin explicitly applies recommended protection:

- Daily Firestore backup schedule retained for 14 days.
- Weekly Firestore backup schedule retained for 84 days.
- Point-in-time recovery (PITR) enabled.
- Firestore database delete protection enabled.
- A READY backup older than 48 hours is surfaced as a recovery-health warning by default.

All thresholds are deployment configuration and can be changed without rebuilding Android. PickupPass clamps configured backup retention to 98 days to stay inside the supported Firestore scheduled-backup retention window.

## Safety model

- Disaster recovery is disabled by default.
- Production startup refuses an enabled DR configuration without a valid GCP project ID.
- Applying protection requires an explicit Master Admin confirmation. Database protection is a Google Cloud long-running operation; PickupPass tracks it asynchronously instead of holding the Android request open.
- PickupPass does not expose a button that disables native backup schedules or delete protection.
- Recovery drills are separately disabled by default even when backup monitoring is enabled.
- A recovery drill restores a selected READY backup to a NEW isolated Firestore database. It never overwrites the production database and never changes the application's production database target.
- There is no automatic production cutover. Any real incident cutover is an infrastructure runbook decision after validation.
- Backup-health telemetry and DR API failures never participate in QR verification, pickup approval, or exit-log creation.

## Credentials and IAM

Prefer Application Default Credentials / workload identity in hosted production environments. `FIRESTORE_DR_CREDENTIALS_PATH` exists only for deployments that genuinely require a credential file. Do not commit service-account JSON keys to this repository.

The backend identity must have only the Google Cloud permissions required to read Firestore database/backup metadata and, when explicitly enabled, manage backup schedules/protection and create isolated restores. Use separate environments/projects for development and production.

## Recovery-drill workflow

1. Master Admin opens **Backup & Disaster Recovery**.
2. Select a READY backup.
3. Enter a meaningful drill reason and the required confirmation phrase.
4. Backend asks Firestore to restore to a generated `pickuppass-recovery-*` database.
5. PickupPass records the long-running operation in `disasterRecoveryJobs`.
6. Master Admin refreshes the job.
7. When the restore completes, PickupPass verifies that the restored database reports the expected backup source.
8. Production is NOT switched automatically.
9. After validation, remove the temporary recovery database using controlled Google Cloud administration according to the recovery runbook. PickupPass intentionally provides no casual delete button for restored databases.

## Health monitoring

Every six hours by default, the backend can materialize a small `disasterRecoveryHealth/global` status document containing:

- PITR enabled state
- delete-protection state
- latest READY backup metadata
- latest backup age
- overall native-protection health
- last check timestamp

This is an operational snapshot only. The Master Admin overview still obtains authoritative Firestore protection information from Google Cloud when requested.

## Retention guardrails

PickupPass distinguishes short-lived technical data from business/safety records.

### TTL candidates

- `securityAuthWindows.expiresAt` — seven-day authentication aggregation telemetry.
- `idempotencyKeys.expiresAt` — seven-day replay/idempotency safety window.

Use Firestore server-side TTL for these fields instead of adding destructive scheduled deletion code to the application.

### No automatic deletion by PickupPass

The application does not automatically delete:

- `exitLogs` / student release records
- privileged/system/school audit history
- invoices, payment notices, payment events, or receipts

A school's legal/business retention obligations vary by jurisdiction and contract. Establish a documented retention policy before enabling deletion for these records. This update is an engineering safeguard, not a legal-compliance determination.

## Environment configuration

```text
FIRESTORE_DR_ENABLED=false
FIRESTORE_DR_ALLOW_RESTORE_DRILLS=false
FIRESTORE_DR_PROJECT_ID=your-google-cloud-project-id
FIRESTORE_DR_DATABASE_ID=(default)
FIRESTORE_DR_CREDENTIALS_PATH=
FIRESTORE_DR_DAILY_RETENTION_DAYS=14
FIRESTORE_DR_WEEKLY_RETENTION_DAYS=84
FIRESTORE_DR_WEEKLY_DAY=SUNDAY
FIRESTORE_DR_HEALTH_INITIAL_DELAY_MS=120000
FIRESTORE_DR_HEALTH_SCAN_MS=21600000
FIRESTORE_DR_MAX_BACKUP_AGE_HOURS=48
```

Enable `FIRESTORE_DR_ENABLED=true` only after IAM, project/database identity, billing, and recovery ownership have been reviewed. Enable restore drills separately.

## New Master Admin API

```text
GET  /api/master-admin/disaster-recovery/overview
POST /api/master-admin/disaster-recovery/protection/recommended
POST /api/master-admin/disaster-recovery/recovery-drills
POST /api/master-admin/disaster-recovery/recovery-drills/{jobId}/refresh
```

All endpoints require `master_admin`.

## Firestore records

- `disasterRecoveryJobs/{jobId}` — isolated restore-drill tracking.
- `disasterRecoveryControl/global` — tracks the long-running database protection update so the UI does not encourage duplicate requests while Google Cloud is still applying it.
- `disasterRecoveryHealth/global` — non-critical materialized recovery-health snapshot.

No new composite Firestore index is required by Update 11.

## Production runbook before launch

- Confirm native backup schedule and PITR/delete protection in Google Cloud, not only in the app.
- Confirm at least one READY backup is visible.
- Run an isolated restore drill in the production project (or an equally representative controlled environment) before launch.
- Verify restored database source metadata and sample critical tenant records.
- Document who may authorize an actual production recovery/cutover.
- Document how the backend database target/configuration is changed during a declared incident.
- Record RTO/RPO targets and rehearse them periodically.
- Enable server-side TTL only for explicitly approved ephemeral collections.
