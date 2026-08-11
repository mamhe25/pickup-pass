# Phase 3 Update 6 — Billing Delivery & Payment Webhook Foundation

## What this update adds

1. **Billing profiles** per school: billing name, billing email, billing address, and tax/registration ID.
2. **Historical invoice snapshots**: newly created invoices copy the billing profile into the invoice record. Later profile edits do not rewrite old invoices.
3. **On-demand PDF invoices** generated server-side with Apache PDFBox.
4. **Invoice email delivery** through the existing Spring Mail/SMTP configuration, with the generated PDF attached.
5. **Provider-neutral payment webhook architecture** so a real payment provider can be added as an adapter instead of coupling billing logic to one vendor.

## Billing email behavior

Set the SMTP environment variables plus `BILLING_FROM_EMAIL`. Email is deliberately optional. If SMTP is unavailable, billing email can fail without affecting invoice PDF generation, subscriptions, or student pickup.

Recipient resolution is:

1. Email explicitly entered by the Master Admin when sending.
2. Current school billing email.
3. Billing-email snapshot stored on the invoice.
4. First active school-admin email as a fallback.

The invoice stores `lastEmailedAt`, `lastEmailedTo`, and `emailDeliveryCount` when bookkeeping succeeds. An email that has already been handed to the SMTP server is still reported as sent even if the later metadata write fails, preventing misleading client retries and accidental duplicate sends.

## PDF behavior

`GET /api/master-admin/billing/invoices/{invoiceId}/pdf`

The PDF is generated from invoice snapshot fields and is returned with `Cache-Control: no-store`. It contains the invoice number, dates, billing identity, plan, amount, note, payment status, and payment reference when available.

## Generic payment webhook contract

Endpoint:

`POST /api/webhooks/payments/generic-hmac`

Required headers:

- `X-Webhook-Event-Id`: unique provider event ID.
- `X-Webhook-Timestamp`: Unix epoch seconds.
- `X-Webhook-Signature`: lowercase/uppercase hexadecimal HMAC-SHA256 signature.

Signature input:

`<timestamp>.<raw request body>`

Secret:

`PAYMENT_WEBHOOK_GENERIC_SECRET`

The timestamp must be within five minutes of the server clock. The raw body is hashed and only the hash is retained in the webhook-event record.

Normalized payload for a successful payment:

```json
{
  "type": "payment.succeeded",
  "occurredAt": "2026-08-11T03:00:00Z",
  "data": {
    "invoiceId": "FIRESTORE_INVOICE_DOCUMENT_ID",
    "amountMinor": 150000,
    "currency": "PHP",
    "paymentReference": "provider-reference"
  }
}
```

`invoice.paid` is also accepted as an equivalent normalized event type.

Before an invoice is marked paid, PickupPass checks that:

- the event signature is valid;
- the timestamp is fresh;
- the event ID has not already been processed;
- the invoice exists and is not void;
- the payment amount exactly matches `amountMinor`;
- the three-letter currency matches the invoice.

The event record and invoice payment update are committed transactionally. Duplicate delivery of the same event ID returns an idempotent duplicate result without paying the invoice twice.

## Adding a real provider later

Create another implementation of `PaymentWebhookAdapter` for the provider and give it a unique `provider()` name. The adapter owns provider-specific signature verification and payload normalization. `PaymentWebhookService` continues to own the common invoice validation, idempotency, and payment-state transition.

This means a future PayMongo, Xendit, Stripe, or other integration can be added without rewriting the invoice ledger or student-facing application.

## Safety boundary

Billing remains operationally separate from student release. SMTP outages, invoice PDF errors, payment-provider downtime, or webhook failures must never prevent a valid QR pickup from being verified and approved.

## Startup manual GCash mode (Update 7)

When subscription payments are received through a personal/manual GCash account, do not emulate a merchant webhook. Use the manual GCash payment-notice flow instead. The provider-neutral webhook architecture remains available for a future merchant/payment-gateway integration, but it is not required for manual GCash collection.
