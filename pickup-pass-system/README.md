# Multi-School Digital Pickup Pass System

PickupPass prevents unauthorized student pickups using signed, single-use QR passes and face-to-face photo verification at dismissal.

For the complete Firestore schema (every collection, every field, and who writes what), see [`SCHEMA.md`](./SCHEMA.md).

For production deployment to Google Cloud Run + Firebase Hosting, and for building a signed Android release, see [`DEPLOYMENT.md`](./DEPLOYMENT.md).

For the complete Windows/PowerShell local-development procedure, including backend `.env`, Firebase Application Default Credentials (ADC), Gmail SMTP, web testing, health checks, and Android setup, see [`LOCAL_DEVELOPMENT.md`](./LOCAL_DEVELOPMENT.md).

> **Security:** Never commit Firebase Admin service-account JSON files, Gmail App Passwords, `.env`, API keys, keystores, signing passwords, or other credentials. Prefer Google Application Default Credentials instead of long-lived service-account JSON keys.

## Structure

```text
backend/     Java Spring Boot API (Firebase Admin SDK)
frontend/    Static HTML/Tailwind/JavaScript web portal
firebase/    Firestore rules, Firebase Hosting config, indexes
```

The native Android application is in the repository-level `pickup-pass-android/` directory.

---

# 1. Firebase Project Setup

PickupPass currently uses the Firebase project `pickuppass`.

For a new environment:

1. Create a Firebase project at the Firebase Console.
2. Enable **Authentication → Email/Password**.
3. Enable **Firestore Database** in production mode.
4. **Do not enable Cloud Storage unless you intentionally move to the Blaze plan.**
   PickupPass currently stores compressed avatars and school logos as base64 data URIs directly in Firestore documents.
5. Configure backend authentication using **Application Default Credentials (ADC)** instead of putting a Firebase Admin private-key JSON file in this repository.

For local Windows development:

```powershell
gcloud.cmd auth application-default login
gcloud.cmd config set project pickuppass
gcloud.cmd config get-value project
```

The final command should print:

```text
pickuppass
```

If PowerShell blocks `gcloud.ps1`, use `gcloud.cmd` as shown above. You do not need to weaken your PowerShell execution policy.

On Google Cloud production workloads such as Cloud Run, use the service account attached to the workload. The backend already supports ADC when `FIREBASE_CREDENTIALS_PATH` is empty.

Only if ADC cannot be used should a credential file be used. In that case, keep it **outside the Git repository**, for example:

```text
C:\Users\<you>\.pickuppass-secrets\firebase-admin.json
```

and reference the external path through `FIREBASE_CREDENTIALS_PATH`.

Never store it under `backend/secrets/` or anywhere else inside this repository.

## Deploy Firestore rules and indexes

Only deploy rules/indexes after changing them:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system
firebase login
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes
```

Select the existing `pickuppass` Firebase project.

---

# 2. Backend — Java / Spring Boot

Requires Java 17+ and Maven.

The backend defaults to the `dev` Spring profile and runs on:

```text
http://localhost:8080
```

## First local setup

From:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system\backend
```

Create your local environment file from the safe template:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and enter your real local values.

Example:

```properties
SPRING_PROFILES_ACTIVE=dev

# Firebase: leave blank when using ADC
FIREBASE_CREDENTIALS_PATH=

# Gmail SMTP
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-google-app-password

# Frontend / CORS
FRONTEND_BASE_URL=http://localhost:5500
CORS_ALLOWED_ORIGINS=http://localhost:5500,http://127.0.0.1:5500
SCHOOL_TIME_ZONE=Asia/Manila
RATE_LIMIT_ENABLED=true

# Security secrets — generate unique values locally
QR_SIGNING_SECRET=replace-with-a-long-random-secret
SECURITY_FINGERPRINT_SECRET=replace-with-a-different-random-secret

# Existing PickupPass environments should keep bootstrap disabled
BOOTSTRAP_ENABLED=false
BOOTSTRAP_SECRET=

DISMISSAL_WINDOW_MINUTES=120
QR_TOKEN_TTL_MINUTES=15
MAX_GUARDIANS_PER_STUDENT=4

FIRESTORE_DR_ENABLED=false
FIRESTORE_DR_ALLOW_RESTORE_DRILLS=false
```

Generate a strong local random secret with:

```powershell
py -c "import secrets; print(secrets.token_urlsafe(48))"
```

Run that twice and use different values for:

```text
QR_SIGNING_SECRET
SECURITY_FINGERPRINT_SECRET
```

Do not reuse production secrets.

## Gmail SMTP

For personal Gmail SMTP:

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-google-app-password
```

Use a Google **App Password**, not your normal Google account password.

## Verify `.env` is protected

```powershell
git check-ignore -v .env
git status --short
```

`.env` must not appear as an untracked or staged file.

The repository intentionally commits `.env.example` but ignores the real `.env`.

## Run backend tests

```powershell
mvn test
```

## Start the backend

```powershell
mvn spring-boot:run
```

A successful startup should report the `dev` profile and Tomcat on port `8080`.

## Health checks

In another PowerShell window:

```powershell
curl.exe http://localhost:8080/actuator/health/liveness
curl.exe http://localhost:8080/actuator/health/readiness
curl.exe http://localhost:8080/actuator/health
```

Expected:

- `liveness` → `UP`
- `readiness` → `UP`
- `firestore` → `UP`
- `mail` → `UP` when SMTP is configured
- overall health → `UP`

If overall health is `DOWN` while liveness/readiness are `UP`, inspect the component details. SMTP authentication is a common local-development cause.

The `dev` profile intentionally enables health details for diagnosis. Production should not expose internal health details publicly.

---

# 3. Backend API

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/api/bootstrap/master-admin` | none — bootstrap secret | Create the first master admin |
| POST | `/api/master-admin/schools` | master_admin | Create a school |
| POST | `/api/master-admin/schools/{schoolId}/status` | master_admin | Activate/suspend a school |
| POST | `/api/master-admin/schools/{schoolId}/staff` | master_admin | Create teacher or school_admin |
| POST | `/api/school-admin/staff` | school_admin | Invite teacher for own school |
| POST | `/api/teacher/students` | teacher, school_admin | Create student |
| POST | `/api/teacher/register-parent` | teacher, school_admin | Create/link primary guardian |
| POST | `/api/parent/add-guardian` | parent | Add backup guardian |
| POST | `/api/parent/remove-guardian` | parent | Remove backup guardian |
| POST | `/api/parent/generate-token` | parent | Generate pickup QR token |
| POST | `/api/pickup/verify` | teacher, school_admin | Verify scanned QR |
| POST | `/api/pickup/approve` | teacher, school_admin | Approve release, log exit, notify guardians |
| POST | `/api/device/register-token` | signed-in user | Register FCM token |
| POST | `/api/device/unregister-token` | signed-in user | Unregister FCM token |
| POST | `/api/master-admin/schools/{schoolId}/logo` | master_admin | Set school logo |
| POST | `/api/school-admin/logo` | school_admin | Set own school logo |

Every endpoint except `/api/bootstrap/master-admin` expects:

```text
Authorization: Bearer <Firebase ID token>
```

Role and `schoolId` come from Firebase custom claims set server-side. Never trust a client-supplied role or tenant identifier.

See [`SCHEMA.md`](./SCHEMA.md) for the data model.

---

# 4. Account Provisioning Model

There is deliberately **no public self-registration**.

Accounts are provisioned down the authorization chain:

```text
bootstrap             → master_admin
master_admin          → school_admin, teacher
school_admin          → teacher
teacher/school_admin  → parent (primary guardian)
parent                → backup guardian
```

There are no default or hardcoded admin credentials.

## First-time setup: creating the first `master_admin`

Only do this for a **brand-new Firebase environment** where no master admin exists.

For an existing PickupPass environment, keep:

```properties
BOOTSTRAP_ENABLED=false
BOOTSTRAP_SECRET=
```

### Step 1 — temporarily enable bootstrap

In the backend `.env`:

```properties
BOOTSTRAP_ENABLED=true
BOOTSTRAP_SECRET=replace-with-a-one-time-random-secret
```

Generate the secret locally:

```powershell
py -c "import secrets; print(secrets.token_urlsafe(48))"
```

Restart the backend.

### Step 2 — call the bootstrap endpoint once

PowerShell example:

```powershell
$bootstrapSecret = "YOUR_BOOTSTRAP_SECRET"

$body = @{
    email = "you@example.com"
    firstName = "Your"
    lastName = "Name"
    middleInitial = ""
    suffix = ""
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/bootstrap/master-admin" `
    -Headers @{ "X-Bootstrap-Secret" = $bootstrapSecret } `
    -ContentType "application/json" `
    -Body $body
```

The backend:

1. verifies bootstrap is explicitly enabled;
2. validates the bootstrap secret;
3. refuses to proceed if any `master_admin` already exists;
4. creates the Firebase Auth user and Firestore profile;
5. sets the `master_admin` custom claim;
6. attempts to send a password-reset/invite email.

If email delivery fails, the account still exists. Use the web app's **Forgot password?** flow for that email address.

### Step 3 — disable bootstrap immediately

After successful creation:

```properties
BOOTSTRAP_ENABLED=false
BOOTSTRAP_SECRET=
```

Restart/redeploy the backend.

Do not leave bootstrap enabled.

---

# 5. Account Creation Reliability

Account-creation operations create the Firebase Auth user and Firestore profile before attempting invite-email delivery.

Email failures therefore do not roll back an otherwise successful account. Responses expose `emailSent` so the UI can accurately report:

- account created and email sent; or
- account created, but the invite email failed — use **Forgot password?**

Existing-account checks only treat Firebase `USER_NOT_FOUND` as a missing user. Other Firebase failures are surfaced instead of incorrectly falling through to duplicate account creation.

Firestore writes in provisioning and safety-critical pickup paths are awaited rather than fire-and-forget.

Unexpected backend failures are logged by the global exception handler with server-side detail while clients receive controlled error responses.

---

# 6. Multi-Guardian Model

Each student can have multiple authorized guardians.

Conceptually:

```text
guardianUids: [uid1, uid2, ...]

guardians:
  uid1:
    relationship: ...
    isPrimary: true
    addedBy: ...
    addedAt: ...

  uid2:
    relationship: ...
    isPrimary: false
    addedBy: ...
    addedAt: ...
```

Rules:

- Teacher/school admin creates the primary guardian.
- Existing guardians can add backup guardians up to `MAX_GUARDIANS_PER_STUDENT` (default `4`).
- A backup guardian receives an independent account and can generate their own QR pass.
- The primary guardian cannot be removed through the parent-facing endpoint.
- Removing a backup guardian immediately invalidates their unused QR passes.

---

# 7. Push Notifications

`users/{uid}` stores an `fcmTokens` array for signed-in devices.

When a pickup is approved:

1. the backend finds every guardian associated with the student;
2. FCM notifications are sent to their registered devices;
3. stale/unregistered tokens are pruned;
4. notification failure does not roll back the safety-critical pickup approval.

On sign-out, Android unregisters the current device token before clearing the Firebase session.

---

# 8. School Branding and Profile Images

PickupPass currently does not require Firebase Cloud Storage for avatars or school logos.

Images are compressed and stored as base64 data URIs in Firestore.

## School logos

The backend `SchoolLogoService`:

- accepts PNG, JPEG, or WebP;
- accepts up to 2 MB raw upload;
- resizes to a maximum 512×512 while preserving aspect ratio;
- avoids upscaling;
- keeps PNG when feasible;
- can fall back to compressed JPEG when needed;
- writes the result to `schools/{schoolId}.logoUrl`.

School admins can update their own school logo.

Master admins can update any school's logo.

## Parent avatars

Parent profile photos are resized/compressed before being written to `users/{uid}.photoUrl`.

The Android application uses its shared `SmartImage` helper to render both normal URLs and `data:` URIs.

---

# 9. Frontend Web Portal

The web portal uses plain static HTML/Tailwind/JavaScript and has no build step.

The existing Firebase client configuration is in:

```text
frontend/shared/firebase-init.js
```

When served from `localhost` or `127.0.0.1`, the frontend automatically uses:

```text
http://localhost:8080/api
```

For deployed Firebase Hosting, it uses same-origin:

```text
/api
```

so normal local testing does not require manually switching the backend URL.

## Run locally

Open another PowerShell:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system\frontend
py -m http.server 5500
```

Open:

```text
http://localhost:5500/login.html
```

## Main web pages

- `login.html` — shared sign-in and role routing
- `parent/students.html` — linked students
- `parent/profile.html` — parent profile/photo
- `parent/pickup-pass.html` — generate pickup QR
- `parent/manage-guardians.html` — manage backup guardians
- `teacher/scanner.html` — scan/verify/release
- `teacher/students.html` — student roster
- `teacher/register-parent.html` — create/link primary guardian
- `school-admin/branding.html` — school branding
- `school-admin/staff.html` — invite teachers

Master-admin screens are web-only.

---

# 10. Android App

The native Android application lives at:

```text
D:\Projects\PickupPass\pickup-pass-android
```

It uses Kotlin, Jetpack Compose, Material 3, Hilt, Firebase Auth/Firestore/FCM, Retrofit, CameraX, ML Kit, and ZXing.

See the repository-level Android [`README.md`](../pickup-pass-android/README.md) for full details.

## Firebase Android client

Register the Android app in Firebase with package:

```text
com.pickuppass.android
```

Download:

```text
google-services.json
```

and place it at:

```text
pickup-pass-android/app/google-services.json
```

The repository ignores this file.

## Local backend URL

For an Android emulator:

```text
http://10.0.2.2:8080/api/
```

For a physical Android device on the same Wi-Fi:

1. run `ipconfig`;
2. find the PC's IPv4 address;
3. point debug `API_BASE_URL` to `http://<PC-IP>:8080/api/`;
4. make sure Windows Firewall permits the connection on the private network.

The current `pickup-pass-android/app/build.gradle.kts` may contain a specific LAN IP for debug testing. Update it when the development PC's IP changes.

Never use a local HTTP endpoint in the production release build.

---

# 11. Recommended Local Test Order

After the environment is configured:

1. Run `mvn test`.
2. Start Spring Boot.
3. Verify `/actuator/health/liveness` is `UP`.
4. Verify `/actuator/health/readiness` is `UP`.
5. Verify Firestore is `UP`.
6. Verify mail is `UP`.
7. Start the static web portal.
8. Sign in using existing test accounts.
9. Test role routing.
10. Run the web pilot checklist.
11. Run the Android debug build.
12. Test one complete safety-critical flow:

```text
Parent selects student
        ↓
Generate signed QR pickup pass
        ↓
Staff scans QR
        ↓
Backend verifies pass
        ↓
Staff visually verifies guardian
        ↓
Approve Release
        ↓
Pass becomes used
        ↓
Exit log written
        ↓
Guardians receive notification
```

Use [`WEB_PORTAL_PILOT_TEST_CHECKLIST.md`](../WEB_PORTAL_PILOT_TEST_CHECKLIST.md) for the broader pilot verification.

---

# 12. Free-Tier Notes

- Firestore reads are intentionally minimized.
- Avatars and logos are compressed to remain well below Firestore's document-size limit.
- No Cloud Storage bucket is required for the current image design.
- No Cloud Functions are required for core business logic; the Spring Boot backend performs server-side operations.
- The backend can run on Google Cloud Run or another suitable host.

---

# 13. Security Summary

PickupPass uses defense in depth:

- Firebase Authentication for identity.
- Firebase custom claims for server-authoritative role and school scope.
- Multi-tenant school isolation enforced by both Firestore rules and backend authorization.
- QR pickup passes are HMAC-signed.
- Pickup tokens are short-lived and single-use.
- Guardian removal invalidates active unused passes.
- Safety-critical writes are awaited.
- Exit logs are append-only.
- Client role/school assertions are not trusted.
- Firebase Admin credentials are not intended to live in source control.
- `.env`, service-account secrets, keystores, and similar credentials are ignored by Git.
- Bootstrap is disabled by default and must be explicitly enabled for first-time provisioning.

Before every push:

```powershell
git status
git diff --cached
```

If any credential is ever accidentally committed:

1. revoke/rotate it immediately;
2. remove it from current source;
3. purge it from Git history;
4. verify old clones cannot reintroduce it.

---

# 14. Production Configuration

Production should not depend on a developer `.env` file.

Use your deployment platform's secret/environment-variable management for:

- `QR_SIGNING_SECRET`
- `SECURITY_FINGERPRINT_SECRET`
- SMTP credentials
- webhook HMAC secrets
- signing credentials
- other production-only secrets

For Firebase Admin on Google Cloud, prefer the workload's attached service account through ADC.

Keep:

```properties
BOOTSTRAP_ENABLED=false
```

after initial provisioning.

Do not expose detailed Actuator health internals publicly in production.

For production deployment details, see [`DEPLOYMENT.md`](./DEPLOYMENT.md).
