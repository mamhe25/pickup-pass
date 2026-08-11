# Phase 3 — Subscription Plans & Tenant Feature Flags

This update adds SaaS plan metadata and server-enforced feature entitlements without adding an online payment provider.

## Plans

- Trial — 100 students, 15 staff, up to 3 campuses; all current feature flags enabled for evaluation.
- Starter — 300 students, 30 staff, 1 campus; advanced reports, bulk import, scheduled announcements, multi-campus, and staff gate restrictions are off by default.
- School — 1,500 students, 150 staff, up to 3 campuses; all current feature flags enabled.
- Enterprise — unlimited plan limits and all current feature flags enabled.

Plan limits are stored/exposed in this update. Student/staff quota metering and hard quota enforcement are intentionally left for the next SaaS operations task so existing schools are not unexpectedly blocked by a migration.

## New school defaults

New schools start with:

- plan: trial
- subscriptionStatus: trialing
- trialEndsAt: 30 days from creation
- featureOverrides: empty map (inherit plan defaults)

Legacy school documents without plan fields are treated as Trial with all current features available, which keeps existing deployments compatible.

## Master Admin controls

The Master Admin console can now:

- view each school's plan and subscription status
- choose Trial, Starter, School, or Enterprise
- set subscription status to trialing, active, past_due, or cancelled
- inherit plan feature defaults
- optionally override individual tenant features

Feature overrides are stored on the school document and audited.

## Server-enforced features

The backend now enforces feature flags for:

- bulk_student_import
- advanced_reporting
- scheduled_announcements
- multi_campus (a single campus remains available on plans without multi-campus)
- guardian_verification
- temporary_guardians
- guardian_pickup_schedules
- manual_override
- staff_gate_restrictions
- device_session_management

Core QR pickup is never disabled by a subscription feature flag. School suspension remains the separate control for disabling a tenant.

## Tenant entitlement API

Authenticated tenant users can read effective plan/features from:

GET /api/tenant/entitlements

The School Admin home screen uses this response to hide feature-specific entries such as advanced reports, bulk import, manual override, guardian verification, and staff gate restrictions when disabled.

## Billing

This update does not charge schools or connect to Stripe/PayMongo/etc. Plans are assigned manually from the Master Admin console. Billing automation can be layered on later without changing the tenant entitlement model.
