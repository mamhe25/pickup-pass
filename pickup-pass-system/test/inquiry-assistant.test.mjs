import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../', import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8');
}

test('login exposes a scoped PickupPass inquiry assistant without altering auth flow', async () => {
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

  assert.match(assistantJs, /\/public\/inquiry\/chat/);
  assert.match(assistantJs, /MAX_MESSAGE_CHARS\s*=\s*800/);
  assert.doesNotMatch(assistantJs, /Authorization/);
  assert.doesNotMatch(assistantJs, /authedFetch/);

  assert.match(assistantCss, /\.pp-inquiry-panel/);
  assert.match(assistantCss, /position:\s*fixed/);
  assert.match(assistantCss, /@media \(max-width: 640px\)/);

  assert.match(security, /requestMatchers\("\/api\/public\/inquiry\/\*\*"\)[\s\S]*?permitAll/);
  assert.match(config, /AI_INQUIRY_ENABLED:false/);
  assert.match(config, /OPENAI_API_KEY:/);
  assert.match(config, /gpt-5\.6-luna/);
});
