# Phase 2 — Scheduled Announcements

School administrators can now either send an announcement immediately or schedule it for a future time.

## Backend
- `POST /api/school-admin/broadcasts/schedule`
- `GET /api/school-admin/broadcasts/history`
- `DELETE /api/school-admin/broadcasts/{broadcastId}`
- Firestore collection: `broadcastJobs`
- Distributed-safe transactional job claiming for multiple backend instances.
- A 10-minute processing lease recovers jobs if a backend instance stops mid-job.
- Scheduled jobs are checked every 30 seconds by default (`BROADCAST_SCHEDULER_MS`).
- Maximum schedule horizon: 90 days.
- Title maximum: 120 characters. Body maximum: 2,000 characters.
- Scheduling, cancellation, delivery, and delivery failure are audited.

## Android
The existing School Admin → Announcements screen now supports:
- Send now
- Schedule date/time
- Recent announcement history
- Status: scheduled / sent / cancelled / failed
- Recipient count for sent announcements
- Cancel pending scheduled announcements

## Required deployment step
A Firestore composite index was added for `broadcastJobs(status, scheduledAt)`.
From `pickup-pass-system`, run:

`firebase deploy --only firestore:indexes`

## Delivery semantics
Scheduled delivery is designed as at-least-once. Transactional claiming prevents normal duplicate sends across multiple server instances. If a server dies after external notification delivery but before the job is marked sent, the lease recovery mechanism can retry the job. This is preferable to silently losing a school announcement.
