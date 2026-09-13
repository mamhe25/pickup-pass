import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../', import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8');
}

test('public demo form submits through the backend instead of faking success', async () => {
  const landing = await read('frontend/index.html');

  assert.match(landing, /id="demoForm"/);
  assert.match(landing, /name="name"/);
  assert.match(landing, /name="email"/);
  assert.match(landing, /name="school"/);
  assert.match(landing, /name="phone"/);
  assert.match(landing, /name="message"/);
  assert.match(landing, /name="website"/);
  assert.match(landing, /\/public\/demo-requests/);
  assert.match(landing, /method:\s*"POST"/);
  assert.match(landing, /JSON\.stringify\(payload\)/);
  assert.doesNotMatch(landing, /front-end-only demo request behavior/);
});

test('demo inquiry requires email ownership before entering the owner inbox', async () => {
  const [security, limiter, publicController, service, emailService, verifyPage] = await Promise.all([
    read('backend/src/main/java/com/pickuppass/config/SecurityConfig.java'),
    read('backend/src/main/java/com/pickuppass/security/RateLimitFilter.java'),
    read('backend/src/main/java/com/pickuppass/controller/PublicDemoRequestController.java'),
    read('backend/src/main/java/com/pickuppass/service/DemoRequestService.java'),
    read('backend/src/main/java/com/pickuppass/service/EmailService.java'),
    read('frontend/verify-demo.html'),
  ]);

  assert.match(security, /requestMatchers\("\/api\/public\/demo-requests\/\*\*"\)[\s\S]*?permitAll/);
  assert.match(limiter, /new Policy\("demo-request",\s*5,\s*3600\)/);
  assert.match(limiter, /new Policy\("demo-verify",\s*10,\s*900\)/);
  assert.match(limiter, /new Policy\("demo-resend",\s*5,\s*3600\)/);

  assert.match(publicController, /@PostMapping\("\/\{requestId\}\/verify"\)/);
  assert.match(publicController, /@PostMapping\("\/\{requestId\}\/resend"\)/);
  assert.match(publicController, /sendVerifiedConfirmation/);

  assert.match(service, /pending_verification/);
  assert.match(service, /emailVerified", false/);
  assert.match(service, /verificationTokenHash/);
  assert.match(service, /hashToken\(token\)/);
  assert.match(service, /pendingDeleteAfter/);
  assert.match(service, /PENDING_RETENTION_HOURS\s*=\s*24/);
  assert.match(service, /VERIFICATION_EXPIRES_MINUTES\s*=\s*30/);
  assert.match(service, /emailVerified", true/);
  assert.match(service, /verifiedAt/);
  assert.match(service, /orderBy\("verifiedAt"/);
  assert.match(service, /demo_request_verified/);
  assert.match(service, /DISPOSABLE_EMAIL_DOMAINS/);
  assert.match(service, /FREE_EMAIL_DOMAINS/);
  assert.match(service, /\.edu\.ph/);
  assert.match(service, /\.gov\.ph/);

  assert.match(emailService, /sendDemoVerification/);
  assert.match(emailService, /Verify your PickupPass demo request/);
  assert.match(emailService, /sendDemoVerifiedConfirmation/);

  assert.match(verifyPage, /Email verification/);
  assert.match(verifyPage, /\/verify/);
  assert.match(verifyPage, /\/resend/);
  assert.match(verifyPage, /history\.replaceState/);
  assert.match(verifyPage, /school-specific email domain is helpful, but it is not required/i);
});

test('platform owner can schedule, message, and review communication history for verified inquiries', async () => {
  const [ownerController, communicationService, emailService, inbox, inboxCss, nav, notificationCenter] = await Promise.all([
    read('backend/src/main/java/com/pickuppass/controller/MasterDemoRequestController.java'),
    read('backend/src/main/java/com/pickuppass/service/DemoRequestCommunicationService.java'),
    read('backend/src/main/java/com/pickuppass/service/EmailService.java'),
    read('frontend/master-admin/demo-requests.html'),
    read('frontend/master-admin/demo-requests.css'),
    read('frontend/shared/master-admin-nav.js'),
    read('frontend/shared/notification-center.js'),
  ]);

  assert.match(ownerController, /hasRole\('master_admin'\)/);
  assert.match(ownerController, /@GetMapping\("\/\{requestId\}\/communications"\)/);
  assert.match(ownerController, /@PatchMapping\("\/\{requestId\}\/status"\)/);
  assert.match(ownerController, /@PostMapping\("\/\{requestId\}\/messages"\)/);
  assert.match(ownerController, /demo_request\.update_sent/);

  assert.match(communicationService, /collection\("communications"\)/);
  assert.match(communicationService, /demoScheduledFor/);
  assert.match(communicationService, /demoScheduleTimezone/);
  assert.match(communicationService, /sendScheduledEmail/);
  assert.match(communicationService, /sendConvertedEmail/);
  assert.match(communicationService, /sendManualUpdate/);
  assert.match(communicationService, /notifyRequester/);
  assert.match(communicationService, /lastCommunicationAt/);

  assert.match(emailService, /sendDemoScheduled/);
  assert.match(emailService, /sendDemoConverted/);
  assert.match(emailService, /sendDemoClosed/);
  assert.match(emailService, /sendDemoUpdate/);

  assert.match(inbox, /id="scheduleDialog"/);
  assert.match(inbox, /id="updateDialog"/);
  assert.match(inbox, /id="historyDialog"/);
  assert.match(inbox, /Send update/);
  assert.match(inbox, /communication history/i);
  assert.match(inbox, /scheduledAt:\s*localDate\.toISOString\(\)/);
  assert.match(inbox, /Intl\.DateTimeFormat\(\)\.resolvedOptions\(\)\.timeZone/);
  assert.match(inbox, /\/communications/);
  assert.match(inbox, /\/messages/);
  assert.match(inbox, /emailTrustLabel/);
  assert.match(inboxCss, /pp-demo-dialog/);
  assert.match(inboxCss, /pp-demo-history/);
  assert.match(inboxCss, /pp-demo-trust--free/);

  assert.match(nav, /label:\s*'Inquiries'/);
  assert.match(nav, /demo-requests\.html/);
  assert.match(notificationCenter, /demo_request/);
  assert.match(notificationCenter, /\/master-admin\/demo-requests\.html/);
});
