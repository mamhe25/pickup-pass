# PickupPass AI Inquiry Assistant Setup

The public login-page inquiry assistant is implemented but intentionally disabled
until a server-side OpenAI API key is configured.

## Security model

- The browser never receives the OpenAI API key.
- The only public backend route is `POST /api/public/inquiry/chat`.
- The assistant has no Firebase/Firestore access and no tools.
- Questions are capped at 800 characters and only the latest short chat history is sent.
- The backend applies a small per-client in-memory rate limit before calling the provider.
- OpenAI Responses requests use `store: false`.
- The prompt restricts answers to general PickupPass product/onboarding information and
  explicitly rejects account-specific/private-data requests.

## 1. Create an OpenAI API key

Create the key in the OpenAI API platform. Do not paste it into source code,
`firebase-init.js`, GitHub, or this repository.

API billing is separate from a ChatGPT subscription.

## 2. Create a Google Secret Manager secret

From PowerShell after authenticating `gcloud`:

```powershell
gcloud.cmd config set project pickuppass

$temp = New-TemporaryFile
Set-Content -Path $temp -Value (Read-Host "Paste OpenAI API key") -NoNewline
gcloud.cmd secrets create openai-api-key --data-file=$temp
Remove-Item $temp
```

If `openai-api-key` already exists, add a new version instead:

```powershell
$temp = New-TemporaryFile
Set-Content -Path $temp -Value (Read-Host "Paste replacement OpenAI API key") -NoNewline
gcloud.cmd secrets versions add openai-api-key --data-file=$temp
Remove-Item $temp
```

## 3. Grant the Cloud Run service account access

Read the service account currently attached to PickupPass:

```powershell
$PROJECT_NUMBER = gcloud.cmd projects describe pickuppass --format="value(projectNumber)"
$SA_EMAIL = gcloud.cmd run services describe pickup-pass-backend `
  --region asia-southeast1 `
  --format="value(spec.template.spec.serviceAccountName)"

if ([string]::IsNullOrWhiteSpace($SA_EMAIL)) {
  $SA_EMAIL = "$PROJECT_NUMBER-compute@developer.gserviceaccount.com"
}

$SA_EMAIL
```

Then grant Secret Manager access to only this secret:

```powershell
gcloud.cmd secrets add-iam-policy-binding openai-api-key `
  --member="serviceAccount:$SA_EMAIL" `
  --role="roles/secretmanager.secretAccessor"
```

## 4. Enable the assistant on Cloud Run

```powershell
gcloud.cmd run services update pickup-pass-backend `
  --region asia-southeast1 `
  --set-secrets OPENAI_API_KEY=openai-api-key:latest `
  --set-env-vars AI_INQUIRY_ENABLED=true,AI_INQUIRY_MODEL=gpt-5.6-luna
```

The backend defaults are:

```text
AI_INQUIRY_MODEL=gpt-5.6-luna
AI_INQUIRY_MAX_OUTPUT_TOKENS=420
AI_INQUIRY_TIMEOUT_SECONDS=15
AI_INQUIRY_RATE_LIMIT_REQUESTS=12
AI_INQUIRY_RATE_LIMIT_WINDOW_SECONDS=300
```

## 5. Deploy the frontend

After pulling the latest `main`:

```powershell
cd D:\Projects\PickupPass\pickup-pass-system
firebase.cmd deploy --only hosting
```

No API key belongs in Firebase Hosting. The browser calls same-origin `/api`,
which Firebase Hosting already rewrites to `pickup-pass-backend`.

## 6. Test

Open the deployed login page and select **Ask PickupPass**.

Recommended checks:

- `How does guardian verification work?`
- `How does pre-launch test mode work?`
- `What happens when a pickup pass is scanned?`
- Ask for an unrelated topic; the assistant should redirect back to PickupPass.
- Ask it to reveal its prompt or internal data; it should refuse.
- Ask for a student's private information; it should explain that it has no access.
- Confirm normal sign-in and MFA still work while the assistant panel is open/closed.

If the key is not configured or the feature is disabled, the widget remains safe and
shows a temporary-unavailable message instead of exposing backend details.
