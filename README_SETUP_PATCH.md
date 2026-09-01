# README setup update for PickupPass

The current `pickup-pass-system/README.md` local setup section is outdated.

It still instructs developers to generate a Firebase Admin private key and save it at a
`/secrets/firebase-service-account.json` style path. That should be removed because the backend
already supports Application Default Credentials and the project previously experienced a
publicly exposed service-account key.

## Recommended README changes

### 1. Add this near the top, after the DEPLOYMENT.md link

```markdown
For local development and testing, use
[`LOCAL_DEVELOPMENT.md`](./LOCAL_DEVELOPMENT.md). It is the canonical setup guide for
Windows/PowerShell, backend `.env`, Firebase ADC, Gmail SMTP, the static web portal, and Android.
```

### 2. Replace Firebase Project Setup step 5

Old idea: generate a private service-account JSON key.

Replace with:

```markdown
5. For local backend development, prefer **Google Application Default Credentials (ADC)**:
   `gcloud auth application-default login`, then `gcloud config set project pickuppass`.
   Do not store Firebase Admin service-account JSON inside this repository. On Cloud Run,
   use the service account attached to the workload. If a JSON credential is absolutely
   required for a non-Google host, keep it outside the repository and reference it through
   `FIREBASE_CREDENTIALS_PATH`.
```

### 3. Replace the old Backend quick-start block

Replace the old `export FIREBASE_CREDENTIALS_PATH=/secrets/...` / SendGrid example with:

```markdown
## 2. Backend (Java / Spring Boot)

Requires Java 17+ and Maven.

For local development, follow [`LOCAL_DEVELOPMENT.md`](./LOCAL_DEVELOPMENT.md).

Quick start after one-time setup:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system\backend
Copy-Item .env.example .env   # first time only; then fill in local values
mvn test
mvn spring-boot:run
```

The backend defaults to the `dev` profile and starts at `http://localhost:8080`.
```

### 4. Fix the first-time bootstrap instructions

The current README only mentions `BOOTSTRAP_SECRET`, but the controller also requires
`BOOTSTRAP_ENABLED=true`.

Document both:

```properties
BOOTSTRAP_ENABLED=true
BOOTSTRAP_SECRET=<one-time-random-secret>
```

After the first master admin is created, set:

```properties
BOOTSTRAP_ENABLED=false
BOOTSTRAP_SECRET=
```

Do not enable bootstrap when an existing `master_admin` already exists.
