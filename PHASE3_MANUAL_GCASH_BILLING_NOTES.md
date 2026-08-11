# PickupPass Phase 3 — Manual GCash Billing for Startup Stage

This update treats GCash as a manual settlement channel rather than pretending a personal GCash account has merchant-webhook capabilities.

## Intended flow

1. Master Admin creates an invoice.
2. School Admin opens **Subscription & Billing** and sees the invoice plus the configured GCash payment instructions.
3. The school sends the exact invoice amount to the configured GCash account.
4. School Admin submits payer name, GCash reference number, and payment date/time.
5. PickupPass records a `pending_review` payment notice. The invoice remains unpaid.
6. Master Admin checks the actual GCash transaction history and confirms or rejects the notice.
7. Only confirmation marks the invoice paid.

## Safety / accounting behavior

- A school-submitted reference does not automatically mark an invoice paid.
- Duplicate active reference submissions are blocked.
- The submitted amount must equal the invoice total.
- Confirmation re-checks invoice ownership and amount.
- Rejection requires a reason and can be resubmitted with a corrected/new reference.
- Invoice PDF can show GCash payment instructions.
- No GCash PIN, OTP, password, or wallet credentials are ever stored.
- No payment screenshot is required by default, reducing unnecessary personal/payment-image retention.
- Billing failures never block a valid QR student pickup.

## Environment variables

Set these only on the backend deployment environment:

- `BILLING_GCASH_ENABLED`
- `BILLING_GCASH_ACCOUNT_NAME`
- `BILLING_GCASH_MOBILE`
- `BILLING_GCASH_NOTE`

The actual account details are not hard-coded into the source package.
