# Pickup Pass — Native Android App

A native Kotlin/Jetpack Compose rewrite of the parent, teacher/guard, and
school-admin experiences, talking to the same Java Spring Boot backend and
Firebase project used by the web version. (Master admin still has no native
screen — it's one REST call, easiest to keep managing via a simple internal
tool or the web build.)

## Features

### Account & session
- Email/password sign-in with inline validation and error messages
- "Forgot password?" → Firebase password-reset email
- Session persists across app restarts; reopening the app skips straight to
  the right home screen based on the signed-in role (no re-login needed)
- Role-based routing: parents, teachers/guards, and school admins each land
  on a different home screen automatically, driven by a Firebase custom
  claim rather than anything the client asserts about itself
- **School branding** — once signed in, the home screen's title bar shows
  the school's logo and name (fetched from `schools/{schoolId}`), so it's
  always obvious which school's account is currently active on this device
- Sign-out cleanly unregisters this device's push-notification token before
  clearing the session, so a shared or handed-down device doesn't keep
  receiving another person's pickup alerts

### Parent features
- **My Students** — a card per linked child (grade, section) for parents
  with more than one student at the school
- **Profile photo** — tap-to-pick an image; it's automatically center-cropped
  to a square, resized to 400×400, and JPEG-compressed down to roughly
  50KB entirely on-device, then base64-encoded and saved directly to the
  parent's Firestore profile — no Cloud Storage bucket involved (see
  "Why no Firebase Storage?" below)
- **Digital pickup pass** — generates a signed, single-use, time-limited QR
  code for one specific child; shows a live countdown to expiry and a
  one-tap "Regenerate Pass" button
- **Manage guardians** — view everyone currently authorized to pick up a
  child (with photo, name, and relationship), add a backup guardian
  (spouse, grandparent, nanny, etc. — up to a configurable per-student cap)
  by name/email/relationship, and remove a backup guardian with a
  confirmation dialog
- **Pickup push notifications** — get a system notification the instant the
  child is released at the gate, including who picked them up, even if the
  parent generating the pass wasn't the one there in person

### Teacher / guard features
- **Live QR scanner** — full-screen camera preview with on-device barcode
  detection (nothing about the camera feed itself ever leaves the phone)
- **Split verification panel** — once a code is scanned and confirmed valid
  server-side, shows the student's name/grade/section on one side and the
  registered guardian's photo on the other, so staff make the final
  face-to-face judgment call themselves
- **One-tap release approval** — a single prominent "Approve Release" action
  that logs an immutable exit-log entry and triggers guardian notifications
- **Inline error handling** — expired passes, already-used passes, and
  wrong-school passes all surface a clear on-screen reason instead of a
  generic failure, then automatically return to scanning
- **Camera permission flow** — a proper request/rationale screen rather than
  a silent failure if camera access hasn't been granted yet
- **Student roster** — add students to the school (name/grade/section),
  see each one's guardian count at a glance (an amber badge flags 0
  guardians registered), reachable from the scanner's header — shared with
  school_admin, since both roles manage the same roster
- **Register Parent** — the actual UI for linking a student's primary
  guardian (name, email, relationship); adding a student automatically
  jumps straight into this next step, since that's almost always what
  you want to do right after

### School admin features
- **Branding screen** — school admins land here after signing in (rather
  than the scanner) since logo management is their primary reason to be in
  the app; pick an image, it uploads immediately with a live preview
- **Logo upload** — sends the picked image straight to the backend as
  multipart form data; resizing/re-encoding happens server-side
  (`SchoolLogoService`), so the app doesn't duplicate that logic — it just
  does a friendly client-side size check (under 2MB) before sending
- **Invite a Teacher** — creates a teacher account scoped to the admin's
  own school (can't be used to create another school_admin or
  master_admin — see SCHEMA.md's provisioning chain)
- **One-tap access to the scanner and student roster** — since a school
  admin is also allowed to run dismissals and manage the roster, buttons
  on the branding screen jump straight there without a separate sign-in

### Honest success/failure feedback
Adding a teacher, registering a parent, or adding a backup guardian all
create the account *before* attempting to send an invite email — so if the
email fails to send (bad SMTP config, rate limit, etc.), the account still
exists. Rather than either hiding that or treating it as a hard failure,
these screens show a distinct **amber warning banner** ("Account created,
but the invite email couldn't be sent — ask them to use 'Forgot password?'")
instead of the green success banner or red error banner — see
`WarningBanner` in `ui/common/CommonComponents.kt`.

### Security-relevant behavior (not just UI)
- QR tokens are verified and single-use-marked entirely server-side; the
  Android app never trusts anything encoded in the QR code itself beyond
  "here's an opaque signed string to send to the backend"
- Removing a backup guardian immediately invalidates any pass they're
  currently holding — reflected automatically, no special client logic
- Every backend call carries a fresh Firebase ID token attached
  automatically by an OkHttp interceptor; no screen has to think about auth
  headers or token refresh itself

## Stack

- **UI:** Jetpack Compose, Material 3, single-activity navigation (`androidx.navigation.compose`)
- **Architecture:** MVVM — one `ViewModel` + `StateFlow<UiState>` per screen
- **DI:** Hilt
- **Auth/Data:** Firebase Auth (Kotlin SDK) + Firestore reads directly from the client (covered by the same security rules as the web app); all writes/mutations go through the Java backend via Retrofit
- **Camera/QR scanning:** CameraX + ML Kit on-device barcode scanning (no network round trip to detect a code)
- **QR generation:** ZXing, rendered as a native `Bitmap`
- **Push notifications:** Firebase Cloud Messaging — parents get a system notification the moment their child is picked up
- **Images:** Coil for loading regular URLs + a custom `SmartImage` for
  decoding base64 data URIs (see "Why no Firebase Storage?" below); a
  custom `ImageCompressor` (center-crop → resize → iterative JPEG quality
  step-down) mirrors the web app's client-side compressor, capping avatar
  size at ~50KB

## Project setup

> **No sign-up screen exists — this app only has sign-in.** Every account
> (parent, teacher, school_admin) is created *for* someone by whoever's
> already a level above them; nobody self-registers. See the backend
> repo's `README.md` section **"First-time setup: creating your
> master_admin"** for how to bootstrap the very first account, and
> `SCHEMA.md` for the full provisioning chain. There are no default/demo
> credentials baked into this app.

### 1. Firebase

Reuse the **same Firebase project** as the backend/web app — same Firestore
rules and users. In the Firebase Console:

1. Project Settings → Add app → **Android**
2. Package name: `com.pickuppass.android`
3. Download `google-services.json` and place it at `app/google-services.json`
   (replacing the placeholder note file there)

No separate step is needed to enable push notifications — Firebase Cloud
Messaging is automatically available for any Android app registered in the
project, using the same `google-services.json`.

**Do not enable Cloud Storage in this project.** As of Feb 3, 2026, Cloud
Storage for Firebase requires the pay-as-you-go Blaze plan (a linked
billing account) even for entirely free-tier usage. This app doesn't need
it — see "Why no Firebase Storage?" below.

### Why no Firebase Storage?

Both the parent's profile photo and the school's logo are stored as
**base64 data URIs directly inside Firestore documents** instead of being
uploaded to a Cloud Storage bucket:

- `ProfileRepository` compresses the picked avatar on-device (see
  `ImageCompressor`) and writes it straight to
  `users/{uid}.photoUrl` as `data:image/jpeg;base64,...` — no upload step,
  no Storage bucket, no `FirebaseStorage` dependency in this project at all.
- The school logo still goes through the backend (`SchoolRepository` sends
  the picked image as multipart form data to `/api/school-admin/logo`),
  but the backend resizes/compresses it and stores the result the same
  way — as a data URI on the school's Firestore document.
- Since Coil's `AsyncImage` doesn't decode `data:` URIs out of the box, a
  small helper — `SmartImage` in `ui/common/CommonComponents.kt` — checks
  whether a given `photoUrl`/`logoUrl` starts with `"data:"` and decodes it
  directly with `BitmapFactory` if so, or falls back to Coil's `AsyncImage`
  for a normal URL otherwise. Every avatar/logo in the app renders through
  `SmartImage` (or through `GuardianAvatar`/`BrandedTitle`, which use it
  internally) rather than `AsyncImage` directly, so this is handled in one
  place.
- Firestore documents can hold up to 1MiB with no billing-plan requirement
  — both compressors are sized to stay comfortably under that even after
  base64's ~33% size inflation (avatars target ~50KB raw / ~67KB encoded).

### 2. Point the app at your backend

`app/build.gradle.kts` sets `API_BASE_URL` per build type:

- **debug** → `http://10.0.2.2:8080/api/` (the Android emulator's alias for your host machine's `localhost:8080`, i.e. the Spring Boot backend running via `mvn spring-boot:run`)
- **release** → replace the placeholder `https://api.pickuppass.app/api/` with your real deployed backend URL

If testing on a **physical device** instead of the emulator, replace
`10.0.2.2` with your machine's LAN IP and add that IP to
`network_security_config.xml` if it's still plain HTTP.

For building a signed release APK and pointing it at a real deployed
backend, see the backend repo's `DEPLOYMENT.md` — Part 3 covers Android
specifically (signing key generation, `release` build config, and
free/no-Play-Store distribution).

### 3. Open in Android Studio

Open the `pickup-pass-android/` folder directly in Android Studio
(Iguana/2023.2+ recommended). It will detect the missing Gradle wrapper jar
and offer to generate it — accept that, or run `gradle wrapper` once
yourself if you have Gradle installed locally. Then **Sync Project** and
run on an emulator or device (min SDK 26 / Android 8.0+).

## App structure

```
app/src/main/java/com/pickuppass/android/
  data/
    model/        Kotlin data classes mirroring backend DTOs + Firestore docs
    remote/       Retrofit API interface + Firebase-ID-token auth interceptor
    repository/   AuthRepository, StudentRepository, GuardianRepository,
                   PickupRepository, ProfileRepository, NotificationRepository,
                   SchoolRepository (logo upload), TeacherRepository (roster +
                   register-parent), SchoolAdminRepository (invite teacher)
  di/              Hilt modules (Network, Firebase)
  navigation/      Screen routes + NavHost
  notification/    FCM service — receives pushes, re-registers refreshed tokens
  ui/
    splash/        Session check → routes to Login or the right home screen
    login/         Email/password sign-in, forgot-password
    parent/
      students/    "My Students" — picker for parents with multiple kids
      profile/     Avatar upload with on-device compression
      pass/        QR pass generation with live countdown
      guardians/   View/add/remove backup pickup guardians
    teacher/
      scanner/       CameraX + ML Kit live scan → split verify screen → approve
      students/      Roster — add students, see guardian counts (shared w/ school_admin)
      registerparent/ Link a student's primary guardian
    schooladmin/
      branding/    Logo upload/preview for school admins
      staff/       Invite a teacher for the school
    common/        Shared components (buttons, avatars, error/warning/success
                   banners, BrandedTitle for the school logo/name in the top bar)
    theme/         Material 3 color scheme, typography
  util/            ImageCompressor, QrCodeGenerator
```

## How the safety-critical flows work natively

- **QR scanning** runs entirely on-device via ML Kit (`ImageAnalysis` +
  `BarcodeScanning`) — the camera feed never leaves the phone. Only the
  decoded token string is sent to `/api/pickup/verify`.
- **Face verification** is structural, not automated: the app deliberately
  does *not* do any on-device face-matching. It fetches the registered
  guardian's photo from Firestore and displays it large, next to the
  student's name/grade/section, so the human at the gate makes the call —
  matching the same "guard looks at both panels" design as the web app.
- **Guardian revocation** (removing a backup guardian) goes through the
  same backend endpoint as the web app, which immediately invalidates any
  live QR pass that guardian is holding — the Android app doesn't need any
  special handling for that, it just reflects Firestore state.

## How push notifications work

1. On sign-in (and again on every app cold-start while still signed in), the
   app fetches the device's current FCM token and calls
   `POST /api/device/register-token`, which the backend stores in an
   `fcmTokens` array on that user's Firestore profile — an array, not a
   single value, because the same person might be signed in on more than
   one device.
2. When a guard taps **Approve Release**, the backend's
   `PushNotificationService` looks up *every* guardian on that student's
   record (not just whoever generated the scanned pass) and sends each of
   them a push via FCM's multicast API.
3. `PickupPassMessagingService` (a `FirebaseMessagingService` subclass)
   receives the push and posts a local system notification — tapping it
   opens the app directly.
4. If a token FCM reports as unregistered/invalid (e.g. the app was
   uninstalled), the backend automatically prunes it from that user's
   `fcmTokens` array so it doesn't keep failing silently forever.
5. On sign-out, the app explicitly unregisters its current token
   (`POST /api/device/unregister-token`) before clearing the Firebase
   session, so a signed-out device stops receiving that person's alerts
   immediately rather than waiting for a future send to fail.

Notification delivery is always best-effort: if FCM is unreachable or a
token is stale, the backend logs it and moves on — a push failure can never
block or roll back the pickup approval itself, since the approval and exit
log are the safety-critical part of the request.

## What's not included

- No offline mode — scanning, verification, and pass generation all require
  connectivity (by design: single-use token state lives server-side so a
  screenshot or a spoofed offline "valid" state can't be used to get past a
  guard).
- No master-admin screen — suspend/activate-school is one REST call; add a
  screen or keep using the web build / a REST client for that.
- No notification preferences UI (e.g. muting alerts, choosing which
  children to be notified about) — every guardian on a student's record
  currently gets every pickup notification for that student.
