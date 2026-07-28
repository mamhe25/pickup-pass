# Deployment Guide (Free Tier)

Covers taking all three pieces — backend, web frontend, Android app — from
"running on your laptop" to "actually deployed," using entirely free
hosting tiers. No credit card required anywhere in this guide.

| Piece | Hosted on | Cost |
|---|---|---|
| Backend (Spring Boot) | Google Cloud Run | Free tier (2M requests/month) |
| Frontend (static HTML/JS) | Firebase Hosting | Free tier (10GB storage, 360MB/day transfer) |
| Android app | Signed release APK, side-loaded | Free (Play Store is optional and costs $25 one-time — not covered here as it's not required) |

Both Cloud Run and Firebase Hosting live under the **same Google Cloud
project as your Firebase project** — Firebase projects *are* GCP projects
under the hood, so there's nothing new to sign up for.

---

## Part 1: Backend → Google Cloud Run

### Why Cloud Run

- Same account/project as Firebase already — no new credentials to manage
- Free tier: ~2 million requests, 360,000 GB-seconds memory, and 180,000
  vCPU-seconds of compute per month. For a single school's pickup traffic
  (a few hundred requests a day at most), you will not come close to this.
- Scales to zero when idle — costs nothing while nobody's using it. The
  tradeoff: the first request after a period of inactivity takes an extra
  2–5 seconds (a "cold start") while a new container spins up. Acceptable
  for this use case; not something a user is likely to even notice.

### 1. Install and authenticate the Google Cloud CLI

Download from https://cloud.google.com/sdk/docs/install, then:

```bash
gcloud init
gcloud config set project YOUR_FIREBASE_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com
```

(`YOUR_FIREBASE_PROJECT_ID` is the same project ID shown in Firebase
Console → Project Settings.)

### 2. Grant the deploy-time service account Firebase permissions

Since the backend now uses Application Default Credentials (no JSON key
file needed in production — see `FirebaseConfig.java`), Cloud Run's
attached service account needs explicit permission to use Firestore and
Firebase Auth:

```bash
PROJECT_ID=$(gcloud config get-value project)
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
SA_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/datastore.user"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/firebaseauth.admin"
```

### 3. Deploy

From the `backend/` folder (the one containing `Dockerfile` and `pom.xml`):

```bash
gcloud run deploy pickup-pass-backend \
  --source . \
  --region asia-southeast1 \
  --allow-unauthenticated \
  --set-env-vars=GOOGLE_CLOUD_PROJECT=pickuppass \
  --set-env-vars QR_SIGNING_SECRET="$(openssl rand -base64 48)",BOOTSTRAP_SECRET="$(openssl rand -base64 32)",MAIL_USERNAME=your-smtp-username,MAIL_PASSWORD=your-smtp-password
```

Cloud Build automatically builds the `Dockerfile` in this folder — no
separate `docker build`/`docker push` step needed.

**`--allow-unauthenticated` is required and correct here** — it means
Cloud Run's own IAM layer doesn't block requests, which is right for this
app because *application-level* auth (Firebase ID tokens, checked by
`FirebaseAuthenticationFilter`) is what actually protects every endpoint.
Without this flag, nobody — including your own frontend — could reach the
API at all without a separate Google Cloud identity token layered on top.

The command prints a **Service URL** when done, e.g.:
```
https://pickup-pass-backend-xxxxxxxxxx-uc.a.run.app
```
Save this — you'll need it in Parts 2 and 3.

### 4. Updating environment variables later

```bash
gcloud run services update pickup-pass-backend \
  --region us-central1 \
  --set-env-vars KEY=newvalue
```

### 5. Redeploying after a code change

Same command as step 3 — `gcloud run deploy` rebuilds and replaces the
running revision. Existing env vars persist across redeploys unless you
explicitly change them.

### 6. Free email sending

`MAIL_USERNAME`/`MAIL_PASSWORD` need real SMTP credentials for account
invite emails to actually send (see the "unexpected error" bug fix — this
is exactly the credential this app needs). Two free options:

**Gmail SMTP** (simplest, ~500 emails/day limit — plenty for a school):
1. Enable 2-Step Verification on the Gmail account you'll send from
2. Google Account → Security → **App passwords** → generate one for "Mail"
3. Set: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=youraccount@gmail.com`, `MAIL_PASSWORD=<the app password>`

**SendGrid** (100 emails/day free forever, more "professional" sender identity):
1. Sign up at sendgrid.com, verify a sender identity
2. Create an API key
3. Set: `MAIL_HOST=smtp.sendgrid.net`, `MAIL_PORT=587`, `MAIL_USERNAME=apikey`, `MAIL_PASSWORD=<your SendGrid API key>`

Add `MAIL_HOST`/`MAIL_PORT` to the `--set-env-vars` list in step 3 if
they're not already the defaults in `application.yml`.

### (Optional) Secrets via Secret Manager instead of plain env vars

For anything more sensitive than local dev, prefer Secret Manager over
plain `--set-env-vars`:

```bash
echo -n "your-smtp-password" | gcloud secrets create mail-password --data-file=-
gcloud run services update pickup-pass-backend \
  --region us-central1 \
  --set-secrets MAIL_PASSWORD=mail-password:latest
```

---

## Part 2: Frontend → Firebase Hosting

### 1. Point the frontend at your deployed backend

Edit `frontend/shared/firebase-init.js`:

```js
export const API_BASE_URL = "https://pickup-pass-backend-xxxxxxxxxx-uc.a.run.app/api";
```

(Use the Service URL from Part 1, step 3, with `/api` appended.)

### 2. Update the hosting rewrite (optional but recommended)

Edit `firebase/firebase.json`'s `rewrites` destination to the same URL, so
requests to `/api/**` on your Hosting domain transparently proxy to Cloud
Run:

```json
"rewrites": [
  { "source": "/api/**", "destination": "https://pickup-pass-backend-xxxxxxxxxx-uc.a.run.app/api" }
]
```

### 3. Deploy

From the `firebase/` folder:

```bash
firebase deploy --only hosting
```

Prints a Hosting URL when done, e.g. `https://your-project-id.web.app`.
That's the link parents/staff would use for the web build (mainly relevant
for the school-admin-only pages — branding, staff invites — since you said
you'll mainly use the Android app for everything else).

### 4. (Recommended hardening) Tighten CORS

`SecurityConfig.java`'s `corsConfigurationSource()` currently allows any
origin (`*`), which was fine for local development against `localhost`/the
emulator. Once you have a real Hosting URL, tighten it:

```java
config.setAllowedOriginPatterns(List.of("https://your-project-id.web.app"));
```

Redeploy the backend (Part 1, step 5) after this change. The Android app
is unaffected either way — CORS is a browser-only mechanism, irrelevant to
native app network requests.

---

## Part 3: Android → Release build

Since you'll mainly use the Android app, this is the one that matters most
day-to-day. "Deploying" it just means producing a signed release APK and
getting it onto devices — no Play Store required for internal school use.

### 1. Point the release build at your deployed backend

In `app/build.gradle.kts`, find the `release` build type and replace the
placeholder:

```kotlin
release {
    buildConfigField("String", "API_BASE_URL", "\"https://pickup-pass-backend-xxxxxxxxxx-uc.a.run.app/api/\"")
    ...
}
```

(Note the trailing slash — matches the format Retrofit's `baseUrl()` expects.)

### 2. Generate a signing key (one-time)

```bash
keytool -genkeypair -v -keystore pickup-pass-release.keystore \
  -alias pickup-pass -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a keystore password and some identity fields.
**Keep this file and its passwords safe** — you need the exact same
keystore to sign every future update; losing it means you can't publish
updates to the same app identity ever again.

### 3. Configure signing in `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("/path/to/pickup-pass-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "pickup-pass"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...existing release config...
        }
    }
}
```

Set `KEYSTORE_PASSWORD`/`KEY_PASSWORD` as environment variables before
building (don't hardcode them in the file, especially if this repo is ever
shared or committed anywhere).

### 4. Build the release APK

In Android Studio: **Build → Generate Signed Bundle / APK → APK** → select
the keystore from step 2 → build. Or from the command line:

```bash
./gradlew assembleRelease
```

Output lands at `app/build/outputs/apk/release/app-release.apk`.

### 5. Distribute it (free — no Play Store needed)

For an internal school tool, the simplest free distribution is direct
install:
- Upload the APK to Google Drive (or any file host) and share the link
- Recipients need "Install from unknown sources" enabled for their browser/
  file manager (a one-time device setting, prompted automatically on
  first install attempt)
- Each new version needs to be manually re-downloaded and reinstalled —
  fine for a small school staff, more friction for a large rollout

**If you later want the Play Store instead** (auto-updates, no "unknown
sources" friction, but $25 one-time developer registration fee and a
review process): that's the only genuinely non-free step anywhere in this
guide, and it's optional — everything else above stays exactly the same
either way.

---

## What changes between local and deployed — quick reference

| Setting | Local value | Deployed value | Where |
|---|---|---|---|
| Backend credentials | `FIREBASE_CREDENTIALS_PATH` set to a JSON file | unset — uses Application Default Credentials | env var |
| Backend port | `8080` | Cloud Run injects `PORT` automatically | `application.yml` (already handles both) |
| Frontend API URL | `http://localhost:8080/api` | your Cloud Run URL | `frontend/shared/firebase-init.js` |
| Hosting rewrite target | n/a (not used locally) | your Cloud Run URL | `firebase/firebase.json` |
| Android API URL (debug) | `http://10.0.2.2:8080/api/` | unchanged — debug builds still point at your dev machine | `app/build.gradle.kts` (`debug` block) |
| Android API URL (release) | placeholder | your Cloud Run URL | `app/build.gradle.kts` (`release` block) |
| CORS allowed origins | `*` | your Hosting URL (recommended) | `SecurityConfig.java` |
| `BOOTSTRAP_SECRET` | any value you set locally | a **new, different** value for prod — rotate it, don't reuse the local one | env var |
| `QR_SIGNING_SECRET` | any value you set locally | a **new, different** value for prod | env var |

The Android **debug** build type is meant to stay pointed at your local
dev backend forever (that's what `10.0.2.2` is for) — you don't need to,
and shouldn't, change it when deploying. Only the `release` build type
needs to know about the real deployed backend.

## Free tier limits worth knowing about

- **Firestore (Spark plan):** 50K reads / 20K writes / 20K deletes per
  day, 1GB stored. A single school easily stays inside this for normal
  daily pickup volume.
- **Cloud Run:** see "Why Cloud Run" above.
- **Firebase Hosting (Spark plan):** 10GB stored, 360MB/day transfer —
  more than enough for a handful of static HTML pages.
- **Gmail SMTP:** ~500 recipients/day.

If a school's usage genuinely outgrows these (very large enrollment, very
high pickup volume), that's the point to reconsider Blaze/paid tiers — not
before.
