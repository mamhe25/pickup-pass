# Phase 2 — Device & Session Management

This cumulative update adds a per-installation session registry for authenticated Android requests.

## User behavior

- Android generates a random installation ID once and stores it in private app preferences.
- Every authenticated API request sends `X-Device-Id` and `X-Device-Name`.
- The backend registers the device on first use and updates `lastSeenAt` at most once every five minutes.
- Parent Profile now includes **My Devices & Sessions**.
- A parent can sign out one device or all other registered devices.
- A revoked device receives HTTP 401 `DEVICE_SESSION_REVOKED`; the existing global Android auth interceptor clears Firebase Auth and returns the app to Login.
- School admins retain the existing **Sign Out All Devices** action for teacher accounts, which revokes Firebase refresh tokens.

## Firestore

New collection: `deviceSessions`. Documents contain uid, schoolId, role, deviceId, deviceName, clientVersion, createdAt, lastSeenAt and revokedAt. No new composite index is required.

## Compatibility

Web clients and older app builds that do not send `X-Device-Id` continue to authenticate through Firebase. Device-level revocation only applies to registered app installations. Use the existing account-level session revocation action when every token must be invalidated.

## Production note

Per-device validation performs a Firestore read for authenticated Android API calls. `lastSeenAt` writes are throttled to once per five minutes per device. If request volume becomes very large, add a short-lived distributed cache while keeping revocation propagation bounded.
