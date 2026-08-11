# Phase 3 Update 5 — Billing Ledger

Adds a master-admin billing ledger for manual-contract SaaS operations.

- Create tenant invoices with amount, ISO currency, due date, and note.
- List the latest 100 invoices per school.
- Mark invoices paid with payment reference/method/note.
- Void unpaid invoices with a reason.
- Reconcile open invoices to overdue after their due date.
- All billing mutations are written to the platform audit log.
- Billing records do not directly suspend a school or block core QR pickup.

Firestore collection: `billingInvoices`.

Required composite index:
- `billingInvoices`: `schoolId ASC`, `createdAt DESC`
