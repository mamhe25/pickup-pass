# PickupPass Firestore Disaster-Recovery Runbook

## Purpose and ownership

This runbook covers native Firestore backup health, isolated recovery drills, and production recovery decisions for PickupPass. The **platform owner / incident commander** owns every recovery. Only an authenticated `master_admin` may use the disaster-recovery API; school administrators must not receive cloud-wide recovery access.

Tenant data exports are portability/support artifacts, not Firestore restore packages. Never use an export as a substitute for a tested native backup.

## Recovery objectives

- **Target RPO:** no more than 24 hours when the startup daily-backup profile is active.
- **Target RTO:** restore and validate an isolated database within 4 hours of declaring recovery.
- Treat both values as operating targets, not guarantees. Record actual RPO/RTO in every drill and incident.
- The default health threshold warns when the newest READY backup is more than 48 hours old (`FIRESTORE_DR_MAX_BACKUP_AGE_HOURS=48`). Investigate immediately; it exceeds the startup RPO target.

## Safety invariants

1. `FIRESTORE_DR_ENABLED` is `false` until project identity, billing, IAM, and recovery ownership are reviewed.
2. `FIRESTORE_DR_ALLOW_RESTORE_DRILLS` is a separate flag and remains `false` except during an approved drill or recovery.
3. Use least-privilege workload identity/Application Default Credentials. Do not commit a service-account key. `FIRESTORE_DR_CREDENTIALS_PATH` is only for deployments that cannot use workload identity.
4. Only a READY backup can be used for a recovery drill, and it must belong to `FIRESTORE_DR_PROJECT_ID` and `FIRESTORE_DR_DATABASE_ID`.
5. Restore always creates a new `pickuppass-recovery-*` database. The job records `productionCutoverAutomatic=false`; it never overwrites the configured production database, changes the application target, or performs automatic production cutover.
6. Never point production traffic at a restored database until validation, security review, incident-commander approval, and a documented cutover/rollback plan are complete.
7. Backup or recovery failures must never block QR verification, pickup approval, or exit-log creation.

## Protection profiles

| Profile | API | Exact confirmation | Intended use |
| --- | --- | --- | --- |
| Free | `POST /api/master-admin/disaster-recovery/protection/free` | `ENABLE FREE SAFEGUARDS` | Delete protection only |
| Startup | `POST /api/master-admin/disaster-recovery/protection/startup` | `ENABLE STARTUP BACKUP` | Delete protection plus one daily backup, 7-day default retention |
| Growth | `POST /api/master-admin/disaster-recovery/protection/recommended` | `ENABLE BACKUP PROTECTION` | Daily and weekly backups, PITR, and delete protection |

Use startup unless the platform owner has approved the added cost and operational need of growth protection. Applying a profile is a controlled configuration change; capture the actor, ticket, response, and follow-up overview.

## Backup-health check

1. Open `GET /api/master-admin/disaster-recovery/overview` as `master_admin`.
2. Confirm `enabled=true`, the expected project/database, and the intended `activeProfile`.
3. Confirm database delete protection and the expected backup schedule.
4. Confirm `latestReadyBackup` exists and its age meets the RPO target.
5. If `databaseProtectionUpdatePending=true`, follow its operation until `databaseProtectionUpdateStatus=applied`; investigate `failed` or `monitor_unavailable`.
6. Confirm the same schedule and READY backup directly in Google Cloud. The application overview is not the sole source of truth.

Escalate if no READY backup exists, the newest backup is older than 24 hours, project/database identity is unexpected, protection is disabled, or a configuration operation fails.

## Incident recovery procedure

### 1. Declare and contain

- Open an incident record, name the incident commander and recovery operator, and record detection/declaration time.
- Identify whether the event is deletion, corruption, credentials compromise, or a regional/service outage.
- Preserve logs and audit records. Rotate compromised credentials before granting recovery access.
- If ongoing writes would worsen corruption, place only the affected write path in maintenance mode. Keep safe read/pickup paths available where possible.
- Record the tenant/data scope and the last known-good timestamp. Do not guess a backup.

### 2. Select the source backup

- Use the overview and Google Cloud to list backups.
- Select a READY backup from the configured production database with a snapshot before the damaging event.
- Record the full backup resource, snapshot time, estimated RPO, selection rationale, and approver.

### 3. Start an isolated restore

Temporarily enable `FIRESTORE_DR_ALLOW_RESTORE_DRILLS=true` only after approval. Call:

- `POST /api/master-admin/disaster-recovery/recovery-drills`
- Body fields: `backupName`, a specific `reason` of at least 10 characters, and `confirmationText` exactly `RESTORE TO ISOLATED DATABASE`.

Record the returned recovery job ID, operation name, target database ID, actor, and start time. Return the flag to `false` when no further restore requests are required.

### 4. Track and verify the restore

- Poll `POST /api/master-admin/disaster-recovery/recovery-drills/{jobId}/refresh` at a controlled interval.
- `running` means wait; `failed` requires investigation and a documented retry decision.
- Proceed only when status is `verified` and `sourceVerified=true`.
- Treat `restore_completed_unverified` as a failed safety check. Do not cut over.

### 5. Validate the isolated database

Connect validation tooling—not production traffic—to the isolated database and record evidence for all checks:

- Restored database metadata names the selected source backup.
- Representative school, student, guardian, pickup-policy, and exit-log counts match the recovery point.
- Sample at least two tenants and prove cross-tenant reads/writes are denied.
- Authentication and roles still enforce `master_admin`, `school_admin`, guard, teacher, and guardian boundaries.
- Used/revoked/expired pickup tokens cannot be replayed; device-session restrictions remain enforced.
- Approve, manual-override, and exit-log flows pass in a non-production validation environment without sending real notifications.
- Audit records are present through the snapshot time; document the expected data gap after it.

### 6. Decide cutover or rollback

PickupPass deliberately has no automatic cutover. The incident commander must approve a separate, reviewed cutover plan that includes application configuration, access controls, downtime, DNS/network considerations, smoke tests, rollback criteria, and stakeholder communication. If validation fails, keep production unchanged and select another recovery point or remediation path.

### 7. Close and clean up

- Confirm production health and critical journeys after any separately approved cutover.
- Retain the recovery evidence and audit trail according to policy.
- Delete the temporary recovery database only through controlled Google Cloud administration after evidence retention is approved; PickupPass exposes no casual delete endpoint.
- Disable restore drills, remove temporary access, close maintenance mode, and schedule corrective actions.
- Record actual RPO, actual RTO, data loss, decisions, approvers, customer impact, and follow-up owners.

## Quarterly restore-drill evidence

Run at least quarterly and after material backup/IAM changes. Record:

- drill date, environment, operator, approver, and ticket;
- selected backup resource and snapshot time;
- job ID, target database, start/completion times, actual RPO/RTO;
- validation results and redacted evidence links;
- cleanup confirmation and temporary-access removal;
- gaps, owners, and due dates.

A backup policy is not considered verified until an isolated restore and the validation checklist have succeeded.
