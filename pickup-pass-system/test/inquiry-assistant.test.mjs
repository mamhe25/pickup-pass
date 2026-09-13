import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../', import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8');
}

test('login exposes a free local PickupPass inquiry assistant without paid API calls', async () => {
  const [login, assistantJs, assistantCss, security, config] = await Promise.all([
    read('frontend/login.html'),
    read('frontend/shared/inquiry-assistant.js'),
    read('frontend/shared/inquiry-assistant.css'),
    read('backend/src/main/java/com/pickuppass/config/SecurityConfig.java'),
    read('backend/src/main/resources/application.yml'),
  ]);

  assert.match(login, /id="inquiryLauncher"/);
  assert.match(login, /id="inquiryPanel"/);
  assert.match(login, /PickupPass Inquiry Assistant/);
  assert.match(login, /Don’t share passwords, authenticator codes, student or guardian records/);
  assert.match(login, /shared\/inquiry-assistant\.css/);
  assert.match(login, /shared\/inquiry-assistant\.js/);

  assert.match(assistantJs, /const KNOWLEDGE = \[/);
  assert.match(assistantJs, /guardian-verification/);
  assert.match(assistantJs, /prelaunch/);
  assert.match(assistantJs, /MAX_MESSAGE_CHARS\s*=\s*800/);
  assert.match(assistantJs, /no paid AI\/API calls/i);
  assert.doesNotMatch(assistantJs, /fetch\s*\(/);
  assert.doesNotMatch(assistantJs, /API_BASE_URL/);
  assert.doesNotMatch(assistantJs, /openai/i);
  assert.doesNotMatch(assistantJs, /Authorization/);

  assert.match(assistantCss, /\.pp-inquiry-panel/);
  assert.match(assistantCss, /position:\s*fixed/);
  assert.match(assistantCss, /@media \(max-width: 640px\)/);

  assert.doesNotMatch(security, /\/api\/public\/inquiry/);
  assert.doesNotMatch(config, /ai-inquiry:/);
  assert.doesNotMatch(config, /OPENAI_API_KEY/);
});
