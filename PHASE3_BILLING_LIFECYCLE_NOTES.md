# Phase 3 Update 4 — Billing Lifecycle & Automated Subscription State

This update adds a production SaaS subscription lifecycle without putting the QR release workflow behind billing.

## New behavior

- New tenants still receive a 30-day Trial.
- Trial expiration automatically moves the tenant to `past_due` and starts a 7-day grace period.
- At the end of grace, the subscription becomes `cancelled` and optional SaaS feature entitlements are disabled.
- Active paid/manual-contract subscriptions use a 30-day service period.
- `autoRenew=true` rolls an expired active period forward automatically.
- `cancelAtPeriodEnd=true` cancels the subscription only after the current period ends.
- Master Admin can start a fresh 30-day billing period, extend a Trial by 30 days, and manually run lifecycle reconciliation.
- Automatic changes are written to the audit log as system actions.

## Safety rule

Subscription state never disables the core guardian QR pickup path, QR verification, pickup approval, or immutable exit logging. Only optional SaaS features in the feature-flag catalog are blocked after subscription access ends.

## Scheduler

The lifecycle job runs every hour by default.

Optional environment setting:

`PICKUPPASS_SUBSCRIPTION_LIFECYCLE_MS=3600000`

## No new Firestore index

This release does not require a new composite index.
