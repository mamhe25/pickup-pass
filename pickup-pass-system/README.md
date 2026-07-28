# Multi-School Digital Pickup Pass System

Prevents unauthorized student pickups via signed, single-use QR passes and
face-to-face photo verification at dismissal.

For the complete Firestore schema (every collection, every field, who
writes what), see [`SCHEMA.md`](./SCHEMA.md).

For deploying this to the free tier of Google Cloud Run + Firebase Hosting
(and building a signed Android release APK), see
[`DEPLOYMENT.md`](./DEPLOYMENT.md).

## Structure

```
backend/     Java Spring Boot API (Firebase Admin SDK)
frontend/    Static HTML/Tailwind/JS pages (parent + teacher UIs)
firebase/    firestore.rules, storage.rules, firebase.json, indexes
```

## 1. Firebase Project Setup (Spark / free plan)

1. Create a project at console.firebase.google.com.
2. Enable **Authentication** → Email/Password provider.
3. Enable **Firestore Database** (production mode).
4. **Do not enable Cloud Storage.** As of Feb 3, 2026, Cloud Storage for
   Firebase requires the pay-as-you-go Blaze plan (a linked billing
   account) even for entirely free-tier usage — enabling it will prompt
   you to upgrade. This app doesn't need it: avatars and school logos are
   stored as base64 data URIs directly in Firestore documents instead (see
   `SchoolLogoService` on the backend and `ProfileRepository` on Android),
   which has no such requirement. If you want real file storage later
   (e.g. for larger files), you can upgrade to Blaze at that point — see
   `firebase/storage.rules` for rules already written for that scenario,
   just not deployed by default.
5. Project Settings → Service Accounts → **Generate new private key**.
   Save the JSON somewhere the backend can read it, e.g. `/secrets/firebase-service-account.json`.
6. From the `firebase/` folder, deploy rules and indexes:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add   # select your project
   firebase deploy --only firestore:rules,firestore:indexes
   ```

## 2. Backend (Java / Spring Boot)

Requires Java 17+ and Maven.

```bash
cd backend
export FIREBASE_CREDENTIALS_PATH=/secrets/firebase-service-account.json
export QR_SIGNING_SECRET=$(openssl rand -base64 48)
export BOOTSTRAP_SECRET=$(openssl rand -base64 32)
export MAIL_USERNAME=apikey
export MAIL_PASSWORD=your_sendgrid_or_smtp_password
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Before doing anything else,
jump to **"First-time setup: creating your master_admin"** below —
nothing in this system is usable yet until that's done, since there's no
sign-up screen anywhere.

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/api/bootstrap/master-admin` | *none — shared secret instead* | create the very first master_admin (see below) |
| POST | `/api/master-admin/schools` | master_admin | create a new school (tenant) |
| POST | `/api/master-admin/schools/{schoolId}/status` | master_admin | activate/suspend a school |
| POST | `/api/master-admin/schools/{schoolId}/staff` | master_admin | create a teacher or school_admin account for any school |
| POST | `/api/school-admin/staff` | school_admin | invite a teacher for their own school only |
| POST | `/api/teacher/students` | teacher, school_admin | create a student roster record for their own school |
| POST | `/api/teacher/register-parent` | teacher, school_admin | create/link a student's **primary** guardian |
| POST | `/api/parent/add-guardian` | parent | add a **backup** authorized pickup guardian to a student |
| POST | `/api/parent/remove-guardian` | parent | revoke a backup guardian (immediately kills any live QR pass they hold) |
| POST | `/api/parent/generate-token` | parent | issue a signed QR pickup token |
| POST | `/api/pickup/verify` | teacher, school_admin | validate a scanned QR (read-only) |
| POST | `/api/pickup/approve` | teacher, school_admin | validate + mark used + write exit log + push-notify guardians |
| POST | `/api/device/register-token` | any signed-in user | register this device's FCM token for push notifications |
| POST | `/api/device/unregister-token` | any signed-in user | remove this device's FCM token (e.g. on sign-out) |
| POST | `/api/master-admin/schools/{schoolId}/logo` | master_admin | upload/replace any school's logo (multipart `file`) |
| POST | `/api/school-admin/logo` | school_admin | upload/replace their own school's logo (multipart `file`) |

Every endpoint except `/api/bootstrap/master-admin` expects
`Authorization: Bearer <Firebase ID token>`. Role and `schoolId` are read
from **Firebase custom claims** set server-side — never trust a
client-supplied role/schoolId. Full field-by-field schema for every
collection these endpoints touch is in [`SCHEMA.md`](./SCHEMA.md).

### There is no self-registration, on purpose

Every account in this system is created *by* someone already in a role
above it — never by the person themselves signing up:

```
bootstrap (one-time) → master_admin
master_admin         → school_admin, teacher   (any school)
school_admin         → teacher                 (their own school only)
teacher/school_admin  → parent                 (primary guardian)
parent                → backup guardian
```

No sign-up screen exists anywhere (web or Android) — deliberately, since
this system exists specifically to control who's allowed to claim a child.
**There are no default/hardcoded admin credentials anywhere in the code.**
The very first account has to be created explicitly, once, using the
bootstrap endpoint below.

### First-time setup: creating your master_admin

1. Set an environment variable before starting the backend:
   ```bash
   export BOOTSTRAP_SECRET=$(openssl rand -base64 32)
   ```
   (On Windows PowerShell: `$env:BOOTSTRAP_SECRET = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))`)

2. With the backend running, call the bootstrap endpoint once:
   ```bash
   curl -X POST http://localhost:8080/api/bootstrap/master-admin \
     -H "X-Bootstrap-Secret: $BOOTSTRAP_SECRET" \
     -H "Content-Type: application/json" \
     -d '{"email":"you@example.com","displayName":"Your Name"}'
   ```
   This creates a Firebase Auth user, sets its `role: master_admin` custom
   claim, and sends a password-reset email to that address so you can set
   your own password (via the same "Forgot password?" flow the login page
   already has — a fresh account has no password set yet, so this step is
   required, not optional).

3. The endpoint **refuses to run again** once any master_admin exists, even
   with the correct secret — so after this one call, rotate/unset
   `BOOTSTRAP_SECRET` in your deployment. It has no further use.

4. Sign in as your new master_admin (web: `login.html`; there's no native
   Android screen for master_admin), then create your first school and its
   school_admin:
   ```bash
   TOKEN=$(# get a fresh Firebase ID token for your master_admin account, e.g. via the Firebase Auth REST API or by signing in on the web build and reading it from devtools)

   curl -X POST http://localhost:8080/api/master-admin/schools \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"schoolName":"Riverside Elementary"}'
   # → { "schoolId": "abc123", "schoolName": "Riverside Elementary" }
   bash
   curl -X POST "https://pickup-pass-backend-445244473897.us-central1.run.app/api/master-admin/schools" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"schoolName":"Riverside Elementary"}'
  
   curl -X POST http://localhost:8080/api/master-admin/schools/abc123/staff \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"email":"admin@riverside.edu","displayName":"Riverside Admin","role":"school_admin"}'
   ```
   bash
   curl -X POST "https://pickup-pass-backend-445244473897.us-central1.run.app/api/master-admin/schools/[shoolId]/staff" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email":"jiesymhe@gmail.com",
    "displayName":"BES Admin",
    "role":"school_admin"
  }'

5. From there, everything is self-serve through the UI: the school_admin
   signs in (web → `school-admin/branding.html`, or the Android app's
   branding screen), uploads a logo, and invites teachers from
   `school-admin/staff.html`. Teachers register parents. Parents add backup
   guardians. No more curl needed after this point.

### Account creation reliability

Every endpoint that creates an account (teacher/school_admin, parent,
backup guardian) follows the same order: **create the Firebase Auth user
and Firestore profile first, then try to send the invite email.** Email
sending can never turn an otherwise-successful account creation into an
error response — `EmailService` catches send failures internally and
returns `false` rather than throwing. Every affected response includes an
`emailSent: boolean` field so the client can tell the admin "account
created, but ask them to use 'Forgot password?' instead" rather than
silently claiming an email went out that never did.

Two related fixes worth knowing about if you're extending this code:
- **Existing-account checks now only treat `AuthErrorCode.USER_NOT_FOUND`
  as "doesn't exist yet."** A previous version caught any
  `FirebaseAuthException` from the lookup and assumed "not found," which
  meant a transient failure (network blip, quota) could fall through to
  `createUser()` and crash with an unhandled `EMAIL_ALREADY_EXISTS` a
  moment later.
- **Firestore writes in account-creation paths are awaited** (`.get()` on
  the `ApiFuture`), not fire-and-forget — a write failure now surfaces as
  a proper error response instead of the request appearing to succeed
  while the data never actually landed. This also applies to the
  safety-critical bits of the pickup flow (`markUsedAndLog`, token
  invalidation on guardian removal) where an unconfirmed write would be a
  real security-relevant bug, not just a UX annoyance.

`GlobalExceptionHandler` also now actually logs every 500 (with a full
stack trace) rather than silently swallowing it — check the server logs
if a client ever reports "Unexpected error" again.

### Multi-guardian model

A student is no longer tied to a single parent. Each `students/{studentId}`
document has:

```
guardianUids: [uid1, uid2, ...]           // for fast "which students are mine" queries
guardians: {
  uid1: { relationship, isPrimary: true,  addedBy, addedAt },
  uid2: { relationship, isPrimary: false, addedBy, addedAt }
}
```

- Teachers create the **primary** guardian (`/api/teacher/register-parent`).
- Any existing guardian can add up to `MAX_GUARDIANS_PER_STUDENT` (default 4)
  backup guardians — a spouse, grandparent, nanny, etc. — via
  `/api/parent/add-guardian`. The backup guardian gets their own login and
  can generate their own independent QR passes.
- The primary guardian can't be removed through the parent-facing endpoint
  (only school staff can reassign that, to avoid an unaccountable record);
  backup guardians can be removed at any time, which immediately invalidates
  any unused QR pass they're currently holding.

### Push notifications

`users/{uid}` also carries an `fcmTokens: [token1, token2, ...]` array (one
entry per signed-in device). When a pickup is approved, `PushNotificationService`
sends a "your child was just picked up" push to **every** guardian on the
student's record — not just whoever generated the scanned QR — so a parent
who wasn't the one picking up still finds out immediately. Notification
failures are logged and swallowed; they never block or roll back the
approval itself, since that's the safety-critical part of the request.
Stale/unregistered tokens are pruned automatically based on FCM's error
response.

### School branding (logos)

Each `schools/{schoolId}` document also carries:

```
logoUrl: string | null        // a base64 data URI, e.g. "data:image/png;base64,..."
logoUpdatedAt: timestamp
```

**Neither parent avatars nor school logos use Firebase/Cloud Storage.** As of
Feb 3, 2026, Cloud Storage for Firebase requires the pay-as-you-go Blaze
plan even for entirely free-tier usage. Instead, both are resized/
compressed down to a small size and stored as base64 **data URIs directly
inside the relevant Firestore document** — `photoUrl` on `users/{uid}`,
`logoUrl` on `schools/{schoolId}`. Firestore documents can hold up to 1MiB
with no billing-plan requirement at all, and both compressors are sized to
stay comfortably under that even after base64's ~33% size inflation.

A school's logo goes **through the backend** via `SchoolLogoService`:

- Accepts PNG, JPEG, or WebP, up to 2MB raw.
- Resizes server-side (max 512×512, preserving aspect ratio, never
  upscaling) using the JDK's built-in `ImageIO`/`Graphics2D` — no extra
  dependency needed. PNGs stay PNG (to preserve a transparent background,
  which most school logos use) *as long as the result stays under ~700KB*;
  if a detailed PNG is still too big even at 512px, it automatically falls
  back to a flattened, quality-stepped JPEG instead (the same iterative
  "try 90% quality, then 80%, then 70%..." approach the avatar compressor
  uses), so it always ends up under Firestore's document-size ceiling.
- The result is base64-encoded and written straight onto
  `schools/{schoolId}.logoUrl` — no bucket, no separate file object, no
  Storage rules involved at all for this path.
- A **school admin** can upload their own school's logo (`/api/school-admin/logo`,
  no `schoolId` needed — it's read from their own claim); a **master admin**
  can set any school's logo directly (`/api/master-admin/schools/{schoolId}/logo`),
  useful for bulk onboarding new schools before they have their own admin
  logged in yet.
- `frontend/school-admin/branding.html` is the web page a school admin sees
  after logging in — pick an image, it uploads and previews immediately.
  The parent (`students.html`) and teacher (`scanner.html`) pages both fetch
  `schools/{schoolId}` on load and show the logo + name in their header, so
  it's always visible which school someone is currently signed into.
- Data URIs render natively in a plain `<img src="...">` on the web — no
  code change needed there. The Android app needed a small addition since
  Coil's `AsyncImage` doesn't decode `data:` URIs out of the box: see
  `SmartImage` in the Android README for how that's handled.

A parent's avatar follows the same pattern client-side: `profile.html`
already compressed the photo down to ~50KB before this change (to fit the
old 100KB Storage-rule cap) — now that compressed blob is just converted to
a data URI via `FileReader.readAsDataURL()` and written directly to
`users/{uid}.photoUrl`, skipping the upload step entirely.

## 3. Frontend

Plain static files — no build step. Fill in your Firebase web config in
`frontend/shared/firebase-init.js`, then serve the folder with any static
server, e.g.:

```bash
cd frontend
npx serve .
```

Pages:
- `login.html` — shared sign-in, routes by role custom claim (parent →
  students list, teacher → scanner, **school_admin → branding page**)
- `parent/students.html` — lists every student the signed-in parent is a guardian for
- `parent/profile.html` — photo upload + compression
- `parent/pickup-pass.html` — QR pass generation for one selected student
- `parent/manage-guardians.html` — view/add/remove backup pickup guardians
- `teacher/scanner.html` — camera scan + face-match approval UI
- `teacher/students.html` — shared roster page (teacher **and** school_admin):
  add students, see guardian counts, jump to registering a parent
- `teacher/register-parent.html` — register a parent as a student's guardian
  (the UI for the `/api/teacher/register-parent` endpoint — this existed on
  the backend for a while before it had a page to call it from)
- `school-admin/branding.html` — upload/replace the school's logo
- `school-admin/staff.html` — invite a teacher for the school

## 4. Free-tier notes

- Firestore reads are minimized: token verification does 2–3 doc reads max.
- Avatars and logos are compressed down to well under Firestore's 1MiB
  per-document limit before being embedded as base64 (avatars target ~50KB
  raw / ~67KB encoded; logos are capped at ~700KB raw / ~930KB encoded).
  No Cloud Storage bucket is used at all, so there's no Blaze-plan billing
  requirement for images — see the "School branding" section above.
- No Cloud Functions are used (Spark plan has no outbound networking for
  functions) — all business logic runs in the Java backend instead, which
  you host wherever you like (Cloud Run, Render, a VPS, etc.).

## 5. Security summary

- Multi-tenant isolation enforced at two layers: Firestore rules (`schoolId`
  match) and backend checks (custom claims), so a compromised client can't
  bypass isolation via either channel alone.
- QR tokens are HMAC-signed JWTs, single-use (tracked by nonce in
  `pickupTokens`), and expire twice over: a short JWT `exp` (~15 min) for
  freshness on screen, and a 2-hour dismissal-window deadline checked
  server-side against `issuedAt`.
- Parent account creation by teachers uses the Admin SDK exclusively, so it
  never disturbs the teacher's own browser auth session.
- `exitLogs` are append-only (no update/delete allowed) for a clean audit
  trail.
