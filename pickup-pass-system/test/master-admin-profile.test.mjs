import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';

async function page(relativePath) {
  return readFile(new URL(`../frontend/${relativePath}`, import.meta.url), 'utf8');
}

async function shared(relativePath) {
  return readFile(new URL(`../frontend/shared/${relativePath}`, import.meta.url), 'utf8');
}

test('platform owner account navigation routes to My profile', async () => {
  const accountLink = await shared('account-link.js');

  assert.match(accountLink, /role === 'master_admin'/);
  assert.match(accountLink, /link\.href = ownerProfile \? '\.\/profile\.html' : '\.\.\/account\.html'/);
  assert.match(accountLink, /ownerProfile \? 'My profile' : 'Account security'/);
  assert.match(accountLink, /\/master-admin\/profile\.html/);
  assert.match(accountLink, /aria-current/);
});

test('platform owner profile reads protected identity and updates display name through backend', async () => {
  const [html, css] = await Promise.all([
    page('master-admin/profile.html'),
    page('master-admin/profile.css'),
  ]);

  assert.match(html, /data-active="profile"/);
  assert.match(html, /requireRole\('master_admin', load\)/);
  assert.match(html, /getDoc\(doc\(db, 'users', user\.uid\)\)/);
  assert.match(html, /profile\.displayName \|\| user\.displayName \|\| 'Platform Owner'/);
  assert.match(html, /apiJson\('\/master-admin\/profile\/name'/);
  assert.match(html, /method:\s*'PUT'/);
  assert.match(html, /JSON\.stringify\(\{ displayName \}\)/);
  assert.match(html, /minlength="2"/);
  assert.match(html, /maxlength="80"/);
  assert.match(html, /href="\.\.\/account\.html"/);
  assert.doesNotMatch(html, /user\.uid[^\n]*textContent|schoolId[^\n]*textContent/);

  assert.match(css, /\.pp-owner-profile-row strong[\s\S]*?overflow-wrap:\s*anywhere/);
  assert.match(css, /@media \(max-width: 560px\)/);
});

test('platform owner signs out from My profile with premium destructive confirmation', async () => {
  const [html, css] = await Promise.all([
    page('master-admin/profile.html'),
    page('master-admin/profile.css'),
  ]);

  assert.match(html, /id="ownerSignOutButton"/);
  assert.match(html, /import \{ confirmDialog \} from '\.\.\/shared\/dialogs\.js'/);
  assert.match(html, /title:\s*'Sign out of PickupPass\?'/);
  assert.match(html, /confirmLabel:\s*'Sign out'/);
  assert.match(html, /danger:\s*true/);
  assert.match(html, /await signOut\(auth\)/);
  assert.match(html, /window\.location\.replace\('\.\.\/login\.html'\)/);
  assert.match(html, /Signing out…/);
  assert.doesNotMatch(html, /window\.confirm\s*\(/);

  assert.match(css, /\.pp-owner-signout-card\s*\{/);
  assert.match(css, /\.pp-owner-signout-card > \.pp-btn[\s\S]*?flex:\s*0 0 auto/);
  assert.match(css, /@media \(max-width: 560px\)[\s\S]*?\.pp-owner-signout-card > \.pp-btn[\s\S]*?width:\s*100%/);
});
