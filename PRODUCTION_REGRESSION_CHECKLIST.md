# PickupPass Production Regression Checklist

Use this checklist after deploying the backend, Firestore configuration, Firebase Hosting, or a new Android build. Run it against the real `pickuppass` project with test accounts and test students only.

## 0. Deployment baseline

- [ ] `git pull origin main` completed on the test machine.
- [ ] Cloud Run `pickup-pass-backend` is on the intended latest revision in `asia-southeast1`.
- [ ] `GET /actuator/health/liveness` returns `UP`.
- [ ] `GET /actuator/health/readiness` returns `UP`.
- [ ] `firebase deploy --only firestore:rules,firestore:indexes` finishes without asking to delete an unmanaged index.
- [ ] `firebase deploy --only hosting` succeeds.
- [ ] Public landing page hard-refreshes with the indigo/violet PickupPass theme and no legacy green marketing cards.
- [ ] Latest Android debug/release build is installed on the test device and points at the deployed Cloud Run API.

## 1. Authentication and account security

### Platform Owner

- [ ] Sign in successfully.
- [ ] Required 2FA appears as the compact six-digit code entry.
- [ ] Paste a six-digit authenticator code and confirm it submits correctly.
- [ ] Wrong code shows one clear error and does not loop/re-submit by itself.
- [ ] Profile & Security shows the correct email verification state without an initial false `Unverified` flicker.

### School Admin

- [ ] Sign in successfully with required 2FA.
- [ ] Account Security opens from Profile/Account Security.
- [ ] Password/email change flows require the expected reauthentication.

### Teacher and Parent

- [ ] Sign in successfully.
- [ ] Optional 2FA behavior remains available where configured.
- [ ] Sign out from Profile works and returns to the login screen.

## 2. Platform Owner → School launch flow

- [ ] Create/open the test school tenant.
- [ ] School name, plan, subscription state, launch state, and usage are human-readable; no raw database IDs are exposed in normal UI.
- [ ] School Admin completes the launch-readiness checklist.
- [ ] School Admin requests launch review.
- [ ] Platform Owner web notification bell increments without refreshing.
- [ ] Platform Owner Android notification badge increments without refreshing.
- [ ] Clicking the Platform Owner notification opens the exact school and focuses `Review launch readiness`.
- [ ] Approval still requires the explicit review/confirmation flow; notification click never auto-approves.
- [ ] Approve the launch.
- [ ] School Admin web notification badge increments without refreshing.
- [ ] School Admin Android notification badge increments without refreshing.
- [ ] Clicking the School Admin notification opens Launch Readiness and shows the approved state.
- [ ] Pre-launch/test-mode restrictions disappear after approval on both web and Android scanner/release workflows.

## 3. School Admin academic structure and staff

- [ ] Create or verify the active academic year.
- [ ] Create a grade/section using the current academic structure flow.
- [ ] Edit the grade/section.
- [ ] Archive/reactivate where supported.
- [ ] Safe-delete protection blocks deletion when records are still referenced.
- [ ] Invite/create a teacher.
- [ ] Assign the teacher only to School Admin-configured sections.
- [ ] Teacher web and Android show only the assigned sections/students.
- [ ] No free-text grade/section entry is available in teacher student registration.

## 4. Student lifecycle

- [ ] School Admin can register a student in an existing grade/section.
- [ ] School Admin can edit the student.
- [ ] Archive the student and confirm the archived state is visible/manageable.
- [ ] Restore the student.
- [ ] Initial School Admin roster filter does not imply the admin is assigned to a teacher section.
- [ ] Search works by student name/number and does not get covered by the mobile keyboard.
- [ ] Bulk-import validation shows valid, invalid, and duplicate counts before import.
- [ ] Import cannot proceed if the file changed after validation.
- [ ] Bulk-import results are human-readable and do not dump raw JSON.

## 5. Guardian onboarding and identity verification

- [ ] Teacher opens a student and adds/invites the primary guardian.
- [ ] Parent cannot generate a pickup QR until the required guardian verification photo is accepted.
- [ ] Parent uploads a verification photo from the gallery.
- [ ] Backend Vision validation accepts a valid face photo.
- [ ] Backend Vision validation rejects an invalid/random image.
- [ ] Repeat with the camera capture option.
- [ ] Guardian photo can be opened/enlarged on Parent and scanner verification screens.
- [ ] Add a backup/temporary authorized guardian using the focused `+` flow.
- [ ] One-day authorization enforces the allowed date window.
- [ ] Primary guardian cannot be removed through the backup-guardian removal flow.

## 6. Pickup pass and dismissal

- [ ] Parent opens an active student and generates a fresh pickup pass.
- [ ] Pass clearly indicates pre-launch test mode when the school is not approved.
- [ ] Pass is blocked for an inactive student.
- [ ] QR can be enlarged for scanning.
- [ ] Countdown/expiry state updates correctly.
- [ ] Expired QR is rejected.
- [ ] Teacher scanner reads a current valid QR.
- [ ] School Admin scanner uses the same production-grade verification flow.
- [ ] Guardian identity/photo is shown before release approval.
- [ ] Staff confirms the guardian and approves the release.
- [ ] Student cannot be released twice from the same pass/event.
- [ ] Exit/dismissal log records the student, guardian, approving staff member, and timestamp.

## 7. Pickup notifications

- [ ] Picker receives copy equivalent to `You picked up <student first name>`.
- [ ] Other guardians receive copy using the picking guardian's first name.
- [ ] Parent unread badge increments by one without refresh.
- [ ] Teacher unread badge increments without refresh for relevant events.
- [ ] Platform Owner and School Admin badges update live for their relevant administrative events.
- [ ] Clicking each notification routes to the related screen when context is available.
- [ ] Mark one notification read decrements the badge correctly.
- [ ] `Mark all read` clears the unread count.
- [ ] Read state survives logout/login.

## 8. Device and session security

- [ ] Parent Profile lists the current device/session correctly.
- [ ] Revoke another device/session using the premium confirmation dialog.
- [ ] Revoked device is signed out on its next authenticated request without requiring a manual refresh/action.
- [ ] Expected first/second blocked requests from the revoked device do not create noisy Platform Owner alerts.
- [ ] Repeated revoked-device attempts cross the configured threshold and surface a security alert.
- [ ] Platform Owner security UI shows human-readable context rather than raw user/school IDs.

## 9. Billing and owner controls

- [ ] Open a test school's billing workspace.
- [ ] Create a test invoice.
- [ ] Manual GCash claim remains pending until owner review.
- [ ] Rejecting a claim requires/shows a clear rejection reason flow and never fails silently.
- [ ] Confirming a payment requires explicit verification/confirmation.
- [ ] Plan/subscription controls open in the focused dialog rather than an overly long page.

## 10. Responsive UI regression

Check at desktop width and a narrow/mobile browser width.

- [ ] Landing page has no horizontal overflow and no legacy green marketing cards.
- [ ] Login remains compact and does not require unnecessary scrolling.
- [ ] Six-digit 2FA boxes fit without clipping.
- [ ] Notification bell/badge is not cropped for Platform Owner, School Admin, Teacher, or Parent.
- [ ] Long dialogs/sheets remain scrollable and usable.
- [ ] Bottom/floating `+` actions do not obscure content.
- [ ] Search fields remain visible when the mobile keyboard opens.
- [ ] Cards in paired metric/workspace layouts have balanced heights.
- [ ] No raw internal IDs, raw JSON responses, or browser-native `alert/confirm/prompt` surfaces appear in normal workflows.

## 11. Android real-device pass

Run on the physical Android test device.

- [ ] Fresh install/run succeeds.
- [ ] Splash/logo/app-list icon show the current PickupPass `P` branding.
- [ ] Platform Owner, School Admin, Teacher, and Parent navigation render correctly.
- [ ] Pull-to-refresh appears once and only on screens designed to support it.
- [ ] Standard centered feedback card appears for success/error/warning actions.
- [ ] Long bottom sheets open fully enough for immediate use without requiring a manual drag.
- [ ] One complete Parent → QR → Teacher/Admin scan → guardian verify → release → notification journey succeeds.

## 12. Final release decision

Do not move to a pilot/release build until all blockers below are clear.

- [ ] No P0/P1 functional bug remains in authentication, QR validation, guardian verification, release approval, tenant isolation, launch restrictions, or session revocation.
- [ ] No required backend/rules/index change is waiting to be deployed.
- [ ] GitHub quality gates are green on the exact release commit.
- [ ] Cloud Run liveness/readiness are `UP` after deployment.
- [ ] Web smoke test is complete on the deployed Firebase Hosting build.
- [ ] Android real-device smoke test is complete against the deployed backend.

When all boxes above pass, proceed to versioning and a signed Android pilot release.
