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

  assert.match(verifyPage, /Email verification/);
  assert.match(verifyPage, /\/verify/);
  assert.match(verifyPage, /\/resend/);
  assert.match(verifyPage, /history\.replaceState/);
  assert.match(verifyPage, /school-specific email domain is helpful, but it is not required/i);
});

test('platform owner sees only verified inquiry confidence and can manage lead status', async () => {
  const [ownerController, inbox, inboxCss, nav, notificationCenter] = await Promise.all([
    read('backend/src/main/java/com/pickuppass/controller/MasterDemoRequestController.java'),
    read('frontend/master-admin/demo-requests.html'),
    read('frontend/master-admin/demo-requests.css'),
    read('frontend/shared/master-admin-nav.js'),
    read('frontend/shared/notification-center.js'),
  ]);

  assert.match(ownerController, /hasRole\('master_admin'\)/);
  assert.match(ownerController, /@GetMapping/);
  assert.match(ownerController, /@PatchMapping\("\/\{requestId\}\/status"\)/);
  assert.match(inbox, /Only email-verified inquiries appear here/i);
  assert.match(inbox, /emailTrustLabel/);
  assert.match(inbox, /emailDomainType/);
  assert.match(inbox, /demo_scheduled/);
  assert.match(inbox, /converted/);
  assert.match(inboxCss, /pp-demo-trust--free/);
  assert.match(nav, /label:\s*'Inquiries'/);
  assert.match(nav, /demo-requests\.html/);
  assert.match(notificationCenter, /demo_request/);
  assert.match(notificationCenter, /\/master-admin\/demo-requests\.html/);
});
