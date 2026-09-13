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
  assert.match(landing, /Request already received/);
  assert.match(landing, /Your PickupPass demo request was received/);
  assert.doesNotMatch(landing, /front-end-only demo request behavior/);
});

test('demo request backend is public only for submission and rate limited', async () => {
  const [security, limiter, publicController, service] = await Promise.all([
    read('backend/src/main/java/com/pickuppass/config/SecurityConfig.java'),
    read('backend/src/main/java/com/pickuppass/security/RateLimitFilter.java'),
    read('backend/src/main/java/com/pickuppass/controller/PublicDemoRequestController.java'),
    read('backend/src/main/java/com/pickuppass/service/DemoRequestService.java'),
  ]);

  assert.match(security, /requestMatchers\("\/api\/public\/demo-requests"\)[\s\S]*?permitAll/);
  assert.match(limiter, /\/api\/public\/demo-requests/);
  assert.match(limiter, /new Policy\("demo-request",\s*5,\s*3600\)/);
  assert.match(publicController, /@PostMapping/);
  assert.match(service, /collection\("demoRequests"\)/);
  assert.match(service, /demo_request_received/);
  assert.match(service, /notifyUsers\(/);
  assert.match(service, /DUPLICATE_WINDOW_MINUTES\s*=\s*15/);
  assert.match(service, /honeypot/);
});

test('platform owner has a protected demo request inbox and notification deep link', async () => {
  const [ownerController, inbox, nav, notificationCenter] = await Promise.all([
    read('backend/src/main/java/com/pickuppass/controller/MasterDemoRequestController.java'),
    read('frontend/master-admin/demo-requests.html'),
    read('frontend/shared/master-admin-nav.js'),
    read('frontend/shared/notification-center.js'),
  ]);

  assert.match(ownerController, /hasRole\('master_admin'\)/);
  assert.match(ownerController, /@GetMapping/);
  assert.match(ownerController, /@PatchMapping\("\/\{requestId\}\/status"\)/);
  assert.match(inbox, /Demo requests/);
  assert.match(inbox, /\/master-admin\/demo-requests/);
  assert.match(inbox, /demo_scheduled/);
  assert.match(inbox, /converted/);
  assert.match(nav, /label:\s*'Inquiries'/);
  assert.match(nav, /demo-requests\.html/);
  assert.match(notificationCenter, /demo_request/);
  assert.match(notificationCenter, /\/master-admin\/demo-requests\.html/);
});
