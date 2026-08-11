# Phase 3 Update 8 — Receipts and Billing Reminders

This update is designed for the current startup/manual-GCash workflow while keeping the billing layer ready for a future payment gateway.

## Added

- Paid-invoice receipt PDF generation.
- School Admin can save both the original invoice PDF and, once paid, a receipt PDF.
- Manual or confirmed GCash payments trigger a best-effort receipt email to the school's billing email.
- Automatic reminder stages: approximately 7 days before due, 1 day before due, and once when overdue.
- Reminder emails include the invoice PDF and tell schools to submit the GCash reference in PickupPass if payment was already sent.
- Reminder delivery is deduplicated using per-invoice timestamps so repeated scheduler scans do not send the same stage repeatedly.
- Master Admin endpoints are available for manual reminder sending and receipt PDF generation.
- Email/receipt/reminder failures do not affect QR pickup, dismissal approval, or exit logging.

## Production settings

Set SMTP and billing sender variables already introduced in Update 6. Optional reminder controls:

```text
BILLING_REMINDERS_ENABLED=true
BILLING_REMINDER_SCAN_MS=21600000
```

The default scan interval is six hours. This does not mean six-hourly email; each reminder stage is sent only once per invoice.

## No new Firestore composite index

This update queries invoices by a single `status` equality filter, so no new composite index is required.
