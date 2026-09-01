# PickupPass Local Development Setup

This guide is the canonical local setup for the PickupPass backend, web portal, and Android app.

> Security rule: never place Firebase Admin service-account JSON, Gmail app passwords,
> keystores, `.env`, or other secrets in Git. The repository `.gitignore` already excludes
> `.env`, `google-services.json`, keystores, and `secrets/`.

## 1. Prerequisites

Install:

- Git
- Java 17+ (`java -version`)
- Maven (`mvn -version`)
- Python 3 (`py --version`) or Node.js for a local static web server
- Google Cloud CLI (`gcloud --version`) for local Firebase Admin credentials
- Android Studio if testing the Android app

The backend currently uses Spring Boot 3.3.x and runs on port `8080` by default.

## 2. One-time Firebase / Google Cloud local authentication

Preferred local setup: Application Default Credentials (ADC). Do not generate a Firebase
Admin JSON key unless ADC is not possible.

```powershell
gcloud auth application-default login
gcloud config set project pickuppass
```

Sign in using the Google account that owns or has access to the `pickuppass` project.

Verify:

```powershell
gcloud config get-value project
```

Expected:

```text
pickuppass
```

If the backend already reports `firestore: UP`, your local Firebase Admin authentication is working.

### Optional fallback: external JSON credential

Only if ADC cannot be used, store a newly-created credential outside the repository, for example:

```text
C:\Users\<you>\.pickuppass-secrets\firebase-admin.json
```

Then set `FIREBASE_CREDENTIALS_PATH` in your local `.env` to that external path.
Never place the JSON anywhere under the PickupPass Git repository.

## 3. Create the backend local `.env`

From:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system\backend
```

Copy the example:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and fill in your local values.

Generate stable development secrets once and keep them in `.env`:

```powershell
py -c "import secrets; print(secrets.token_urlsafe(48))"
```

Use one output for `QR_SIGNING_SECRET`, and generate a second output for
`SECURITY_FINGERPRINT_SECRET`.

### Gmail SMTP

If using personal Gmail SMTP:

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-google-app-password
```

Use a Google **App Password**, not your normal Google password.

The app password is shown by Google only once. If you lose it, create a new one.

## 4. Verify `.env` is ignored by Git

```powershell
git check-ignore -v .env
git status --short
```

`.env` must not appear in `git status`.

`.env.example` and `application-dev.yml` are safe to commit because they contain placeholders only.

## 5. Run backend tests

From the backend directory:

```powershell
mvn test
```

Then start the backend:

```powershell
mvn spring-boot:run
```

The default profile is `dev`, and the API starts at:

```text
http://localhost:8080
```

## 6. Verify backend health

In a second PowerShell window:

```powershell
curl.exe http://localhost:8080/actuator/health/liveness
curl.exe http://localhost:8080/actuator/health/readiness
curl.exe http://localhost:8080/actuator/health
```

Expected:

- liveness: `UP`
- readiness: `UP`
- Firestore: `UP`
- mail: `UP` when SMTP is configured correctly
- overall health: `UP`

If overall health is `DOWN` but liveness/readiness are `UP`, inspect the detailed dev health output.
A common cause is SMTP authentication.

## 7. Run the web portal locally

The frontend is static HTML/JavaScript and has no build step.

Open a third PowerShell:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system\frontend
py -m http.server 5500
```

Open:

```text
http://localhost:5500/login.html
```

The existing `frontend/shared/firebase-init.js` automatically uses:

```text
http://localhost:8080/api
```

when the frontend is served from `localhost` or `127.0.0.1`, so you do not need to edit the
API URL for normal local web testing.

## 8. Existing accounts vs first-time bootstrap

If your Firebase project already has a `master_admin`, keep:

```properties
BOOTSTRAP_ENABLED=false
BOOTSTRAP_SECRET=
```

Do not bootstrap again.

For a brand-new Firebase project only:

1. Set `BOOTSTRAP_ENABLED=true`.
2. Generate a one-time `BOOTSTRAP_SECRET`.
3. Restart the backend.
4. Call `POST /api/bootstrap/master-admin`.
5. After successful creation, immediately set `BOOTSTRAP_ENABLED=false` and clear/rotate the secret.

The endpoint is intentionally disabled by default and refuses additional master-admin creation once
one already exists.

## 9. Firebase rules/indexes

You do not need to redeploy Firestore rules every time you run locally.

Only after changing rules or indexes:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system
firebase login
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes
```

Select the existing `pickuppass` Firebase project.

## 10. Android local setup

### Firebase client configuration

If missing, download the Android `google-services.json` from:

Firebase Console -> Project settings -> Your apps -> Android app

Package:

```text
com.pickuppass.android
```

Place it at:

```text
D:\Projects\PickupPass\pickup-pass-android\app\google-services.json
```

The file is ignored by this repository.

### Backend URL

Current code defines a debug API URL in:

```text
pickup-pass-android/app/build.gradle.kts
```

For an Android emulator, use:

```text
http://10.0.2.2:8080/api/
```

For a physical Android device on the same Wi-Fi as the development PC:

1. Run `ipconfig`.
2. Find the PC's IPv4 address, for example `192.168.1.17`.
3. Use `http://<PC-IP>:8080/api/`.
4. Ensure Windows Firewall allows the backend on the private network.

The current repository contains a hardcoded physical-device LAN IP in the debug build config,
so check it whenever your router assigns your PC a different address.

### Run

Open:

```text
D:\Projects\PickupPass\pickup-pass-android
```

in Android Studio, sync Gradle, select an emulator/device, and Run the `debug` build.

## 11. Recommended local test order

1. Backend starts without exceptions.
2. `/actuator/health/liveness` is `UP`.
3. `/actuator/health/readiness` is `UP`.
4. Firestore health is `UP`.
5. Mail health is `UP`.
6. Web login page loads.
7. Sign in with an existing test account for each role.
8. Run the web pilot checklist in `WEB_PORTAL_PILOT_TEST_CHECKLIST.md`.
9. Run Android login and role routing.
10. Test one full pickup flow: parent generates QR -> staff verifies -> staff approves -> exit log appears -> guardian notification is delivered.

## 12. Never commit these

Never commit:

- `.env`
- Firebase Admin service-account JSON
- Gmail App Passwords
- API keys or webhook secrets
- `google-services.json` if following the current repository policy
- Android keystores
- signing passwords
- production secret values

Before every push:

```powershell
git status
git diff --cached
```

If a credential is ever committed, revoke/rotate it immediately before doing anything else.
