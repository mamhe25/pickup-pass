# PickupPass Phase 3 — Cumulative Update 9
## SaaS Operations Health + Subscription/Billing Reconciliation Dashboard

This update gives the `master_admin` a single operational view of every school tenant without mixing billing state into the child-pickup safety path.

## Health states

Each tenant is classified by the highest active operational risk:

- `healthy`
- `attention_needed`
- `billing_risk`
- `over_quota`
- `suspended`

Suspension is always explicit; billing alerts never silently suspend a school.

## Alert sources

The dashboard materializes deduplicated alerts from existing source-of-truth records:

- GCash payment notices waiting for manual verification
- overdue invoices
- invoices due within 3 days
- trials ending within 7 days
- non-renewing subscription periods ending within 7 days
- billing grace periods ending within 3 days
- past-due/cancelled subscriptions
- student/staff/campus quota at or above the configured warning threshold
- quota already exceeded after a plan downgrade or data reconciliation
- billing email/receipt/reminder delivery failures
- explicitly suspended tenants

## Dynamic refresh strategy

PickupPass does not recompute the entire SaaS state every time a screen is opened.

- A full background reconciliation runs once per day by default.
- Hourly subscription lifecycle transitions and invoice-overdue transitions trigger tenant-scoped health refreshes immediately.
- The master dashboard lazily refreshes if the materialized view is stale.
- GCash payment submissions create an alert immediately.
- GCash confirm/reject resolves and recomputes that tenant immediately.
- Billing email failures create an alert immediately; a later successful delivery resolves it.
- Plan, subscription, school-status, staff/quota, and billing mutations refresh only the affected tenant.
- Master Admin can explicitly run **Refresh operations**.

This provides responsive startup operations while avoiding unnecessary platform-wide Firestore scans.

## New backend endpoints

- `GET /api/master-admin/operations/overview`
- `POST /api/master-admin/operations/refresh`

Both require `master_admin`.

## New Firestore records

### `saasOperationalAlerts/{deterministicHash}`

Alerts use deterministic IDs generated from alert type + resource ID. Repeated scans update the same record instead of creating duplicates.

Important fields:

- `schoolId`
- `schoolNameSnapshot`
- `type`
- `severity`
- `healthImpact`
- `title`
- `message`
- `action`
- `resourceId`
- `active`
- `firstSeenAt`
- `lastSeenAt`
- `resolvedAt`

### `saasOperationsMetadata/global`

Stores the most recent full-scan timestamp and summary information.

## Billing email observability

`billingInvoices` now records a delivery failure state when invoice, receipt, or reminder email delivery fails:

- `emailDeliveryFailed`
- `lastEmailFailureAt`
- `lastEmailFailureType`
- `lastEmailFailureMessage`

A successful later delivery clears the failure state and resolves the operations alert.

## Android Master Admin console

The SaaS console now shows:

- healthy school count
- attention-needed count
- billing-risk count
- over-quota count
- pending GCash review count
- overdue invoice count
- expiring subscription count
- quota-warning count
- billing-email failure count
- prioritized actionable alert cards
- per-school health label on each tenant card

Alert actions take the operator directly to the relevant billing or subscription workflow.

## Firestore indexes

No new composite index is required. Update 9 uses single-field status/boolean/schoolId queries and sorts small operational results in the backend.

## Safety boundary

Billing health, email failures, quota warnings, and invoice status do not participate in QR verification or student-release transactions. Core pickup remains isolated from the SaaS commercial operations layer.

